package dev.t1m3.qplayer.audio.mix;

public final class TempoBridge {

    public static final double MIN_SPEED = 0.75;

    public static final double MAX_SPEED = 1.33;
    private TempoBridge() { }

    public static float[][] stretch(float[][] input, int rate, int frames, double speed) {
        return stretchCore(input, rate, frames, speed, null);
    }

    public static float[][] stretchRamp(float[][] input, int rate, int frames,
                                        double startSpeed, double endSpeed) {
        if (!Double.isFinite(startSpeed) || !Double.isFinite(endSpeed)
                || startSpeed < MIN_SPEED || startSpeed > MAX_SPEED
                || endSpeed < MIN_SPEED || endSpeed > MAX_SPEED)
            throw new IllegalArgumentException("Unsupported tempo ramp");
        return stretchCore(input, rate, frames, startSpeed,
                new double[]{startSpeed, endSpeed});
    }

    public static float[][] stretchConverge(float[][] input, int rate, int frames,
                                            double startSpeed, double endSpeed,
                                            double convergeFrac) {
        if (!Double.isFinite(startSpeed) || !Double.isFinite(endSpeed)
                || startSpeed < MIN_SPEED || startSpeed > MAX_SPEED
                || endSpeed < MIN_SPEED || endSpeed > MAX_SPEED)
            throw new IllegalArgumentException("Unsupported tempo converge");
        double f = Math.min(1.0, Math.max(1e-3, convergeFrac));
        return stretchCore(input, rate, frames, startSpeed,
                new double[]{startSpeed, endSpeed, f});
    }

    public static int requiredFramesConverge(int frames, int rate, double startSpeed,
                                             double endSpeed, double convergeFrac) {
        return requiredFramesRamp(frames, rate, startSpeed, endSpeed);
    }

    public static int requiredFramesRamp(int frames, int rate, double startSpeed, double endSpeed) {
        return (int) Math.ceil(frames * (startSpeed + endSpeed) / 2.0)
                + (int) Math.ceil(rate * .044) + 4;
    }

    private static float[][] stretchCore(float[][] input, int rate, int frames, double speed,
                                         double[] ramp) {
        double consumed = ramp == null ? speed
                : (ramp[0] + ramp[1]) / 2.0;
        if(input==null || input.length==0 || frames<=0 || rate<=0
                || !Double.isFinite(consumed) || consumed<MIN_SPEED || consumed>MAX_SPEED)
            throw new IllegalArgumentException("Unsupported tempo bridge");
        int grain=Math.max(32,(int)Math.round(rate*.04));
        int hop=grain/2, search=Math.max(4,(int)Math.round(rate*.004));
        int available=input[0].length;
        for(float[] ch:input) available=Math.min(available,ch.length);

        long need = ramp == null
                ? (long) Math.ceil(frames * speed)
                : (long) Math.ceil(frames * (ramp[0] + ramp[1]) / 2.0);
        if(available<(Math.abs(speed-1)<1e-6 && ramp==null ? frames : need+grain+search))
            throw new IllegalArgumentException("Incomplete tempo material");
        float[][] output=new float[input.length][frames];
        for(int c=0;c<input.length;c++) System.arraycopy(input[c],0,output[c],0,Math.min(grain,frames));
        if(ramp==null && Math.abs(speed-1)<1e-6) {
            for(int c=0;c<input.length;c++)System.arraycopy(input[c],0,output[c],0,frames);
            return output;
        }

        final double[] win = new double[Math.max(1, hop)];
        for (int i = 0; i < hop; i++) {
            win[i] = 0.5 - 0.5 * Math.cos(Math.PI * i / hop);
        }
        final int chans = input.length;
        for(int pos=hop;pos<frames;pos+=hop) {

            int expected;
            if (ramp == null) {
                expected = (int)Math.round(pos*speed);
            } else if (ramp.length == 2) {
                expected = (int)Math.round(ramp[0]*pos
                        + (ramp[1]-ramp[0])*(double)pos*(double)pos/(2.0*frames));
            } else {
                double f = Math.max(1e-3, ramp[2]);
                double turn = f*frames;
                double u = Math.min(pos, turn);
                double acc = ramp[0]*u + (ramp[1]-ramp[0])*u*u/(2.0*turn);
                if (pos > turn) {
                    acc += ramp[1]*(pos-turn);
                }
                expected = (int)Math.round(acc);
            }
            int low=Math.max(0,expected-search), high=Math.min(available-grain,expected+search);
            int overlap=Math.min(hop,frames-pos), best=expected;
            double bestScore=-Double.MAX_VALUE;

            int at=low;
            for(;at+12<=high;at+=16) {
                double xx=0;
                double xy0=0,yy0=0,xy1=0,yy1=0,xy2=0,yy2=0,xy3=0,yy3=0;
                for(int i=0;i<overlap;i+=4) for(int c=0;c<chans;c++) {
                    float[] oc=output[c];
                    float[] ic=input[c];
                    double x=oc[pos+i];
                    double y0=ic[at+i],y1=ic[at+4+i],y2=ic[at+8+i],y3=ic[at+12+i];
                    xx+=x*x;
                    xy0+=x*y0;yy0+=y0*y0;
                    xy1+=x*y1;yy1+=y1*y1;
                    xy2+=x*y2;yy2+=y2*y2;
                    xy3+=x*y3;yy3+=y3*y3;
                }
                double s0=xy0/Math.sqrt(Math.max(1e-20,xx*yy0))-.005*Math.abs(at-expected)/Math.max(1,search);
                if(s0>bestScore){bestScore=s0;best=at;}
                double s1=xy1/Math.sqrt(Math.max(1e-20,xx*yy1))-.005*Math.abs(at+4-expected)/Math.max(1,search);
                if(s1>bestScore){bestScore=s1;best=at+4;}
                double s2=xy2/Math.sqrt(Math.max(1e-20,xx*yy2))-.005*Math.abs(at+8-expected)/Math.max(1,search);
                if(s2>bestScore){bestScore=s2;best=at+8;}
                double s3=xy3/Math.sqrt(Math.max(1e-20,xx*yy3))-.005*Math.abs(at+12-expected)/Math.max(1,search);
                if(s3>bestScore){bestScore=s3;best=at+12;}
            }
            for(;at<=high;at+=4) {
                double xy=0,xx=0,yy=0;
                for(int i=0;i<overlap;i+=4) for(int c=0;c<chans;c++) {
                    double x=output[c][pos+i],y=input[c][at+i];
                    xy+=x*y;xx+=x*x;yy+=y*y;
                }
                double score=xy/Math.sqrt(Math.max(1e-20,xx*yy))-.005*Math.abs(at-expected)/Math.max(1,search);
                if(score>bestScore){bestScore=score;best=at;}
            }
            for(int i=0;i<grain && pos+i<frames;i++) {
                double w=i<hop?win[i]:1;
                for(int c=0;c<chans;c++)
                    output[c][pos+i]=(float)(output[c][pos+i]*(1-w)+input[c][best+i]*w);
            }
        }
        return output;
    }

    public static int requiredFrames(int frames,int rate,double speed) {
        return (int)Math.ceil(frames*speed)+ (int)Math.ceil(rate*.044)+4;
    }
}
