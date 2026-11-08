"""Generate data-only Kotlin preset tables from the pinned canonical PC catalogue."""
import base64, json, math, re, subprocess
from pathlib import Path

revision = 'a8f1bd0091db9fd103144909ff36257d6de54b2f'
def read(path):
    meta = json.loads(subprocess.check_output(['gh','api','repos/moekotori/echosteam/contents/'+path+'?ref='+revision],text=True))
    return base64.b64decode(meta['content']).decode()
source = read('src/shared/audio/eqBuiltInPresets.ts')
frequencies = [20,25,31.5,40,50,63,80,100,125,160,200,250,315,400,500,630,800,1000,1250,1600,2000,2500,3150,4000,5000,6300,8000,10000,12500,16000,20000]
def curve(anchors):
    values=[]
    for f in frequencies:
        if f <= anchors[0][0]: value=anchors[0][1]
        elif f >= anchors[-1][0]: value=anchors[-1][1]
        else:
            a,b=next((a,b) for a,b in zip(anchors,anchors[1:]) if a[0]<=f<=b[0])
            value=a[1]+(b[1]-a[1])*math.log(f/a[0])/math.log(b[0]/a[0])
        values.append(math.floor(value*10+.5)/10)
    return values
def number(v): return ('%.6g'%v)+'f'
items=[('flat','Flat',0,[0]*31)]
for m in re.finditer(r"preset\('([^']+)', '([^']+)', ([-\d.]+), (\[\[.*?\]\])",source):
    items.append((m[1],m[2],float(m[3]),curve(json.loads(m[4]))))
target=Path(__file__).resolve().parents[1]/'src/main/java/app/echo/android/model/playback/EchoPcEqPresets.kt'
lines=['package app.echo.android.model.playback','','// Generated data from moekotori/echosteam '+revision+'.','// Run core/model/scripts/generate-pc-eq-presets.py; no TypeScript runtime is bundled.','internal object EchoPcEqPresets {','    val frequencies = listOf('+', '.join(map(number,frequencies))+')','    val definitions = listOf(']
for id,name,preamp,gains in items:
    lines.append('        EchoEqualizerPresetDefinition('+json.dumps(id)+', '+json.dumps(name)+', listOf('+', '.join(map(number,gains))+'), '+number(preamp)+'),')
lines+=['    )','}','']
target.write_text('\n'.join(lines),encoding='utf-8')
print('Generated',len(items),'PC presets with',len(frequencies),'exact fixed centers.')
