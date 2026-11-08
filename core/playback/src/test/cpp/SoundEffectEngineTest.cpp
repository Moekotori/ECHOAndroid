#include "EchoSoundEffectEngine.h"
#include <array>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <vector>

using echo_android::SoundEffectEngine;
static void check(bool condition, const char* reason) { if (!condition) { std::cerr << reason << '\n'; std::exit(1); } }
static constexpr std::array<const char*, 22> presets {
    "vocal","bass","warm","wide","live","hall","tape","night","headphones","echo","tube","vinyl",
    "autopan","deess","tremolo","phaser","transient","flanger","telephone","digital","autowah","ringmod"
};

int main() {
    for (const char* preset : presets) {
        for (int channels : {1, 2, 3, 8}) {
            SoundEffectEngine graph(preset, 48000, channels);
            double difference = 0;
            std::vector<float> block(1024 * channels), original(block.size());
            for (int n = 0; n < 8; ++n) {
                for (size_t i = 0; i < block.size(); ++i) {
                    const int sample = n * 1024 + static_cast<int>(i) / channels;
                    const int c = static_cast<int>(i) % channels;
                    block[i] = .21f * std::sin(sample * .071 + c * .4) + .07f * std::sin(sample * .83);
                }
                original = block; graph.process(block.data(), 1024, true);
                for (size_t i = 0; i < block.size(); ++i) {
                    check(std::isfinite(block[i]), "non-finite sound-effect sample");
                    difference += std::abs(block[i] - original[i]);
                }
            }
            // Auto pan intentionally preserves an unpaired mono channel, as on PC.
            const bool monoBypass = channels == 1 && (std::string(preset) == "autopan" || std::string(preset) == "headphones");
            if (!monoBypass && difference <= .01) std::cerr << "Preset: " << preset << ", channels: " << channels << '\n';
            if (!monoBypass) check(difference > .01, "effect was a silent no-op");
            check(graph.tailRemaining() <= 480000, "unbounded tail");
            original = block; graph.process(block.data(), 1024, false);
            check(graph.dry(), "effect did not fade to dry");
            for (size_t i = block.size() - 128 * channels; i < block.size(); ++i)
                check(block[i] == original[i], "deactivation altered the original samples");
            graph.reset(); check(graph.tailRemaining() == 0 && graph.dry(), "reset retained old state");
        }
    }
    // Delay uses the sample clock and survives arbitrary chunk boundaries.
    for (const char* preset : {"echo", "phaser", "flanger", "autowah"}) {
        SoundEffectEngine a(preset, 48000, 2), b(preset, 48000, 2);
        std::vector<float> x(16384), y;
        for (size_t i = 0; i < x.size(); ++i) x[i] = .2f * std::sin(i * .03);
        y = x;
        for (int i = 0; i < 8192; i += 1024) a.process(x.data() + i * 2, 1024, true);
        for (int i = 0; i < 8192; i += 127) b.process(y.data() + i * 2, std::min(127, 8192 - i), true);
        for (size_t i = 0; i < x.size(); ++i) check(std::abs(x[i] - y[i]) < .00001f, "effect changed with PCM chunk size");
    }
    bool rejected = false;
    try { SoundEffectEngine invalid("unknown", 48000, 2); } catch (...) { rejected = true; }
    check(rejected, "unknown preset accepted");
    std::cout << "22 presets x 4 channel layouts: native processing, fade/reset, bounded tails, sample-clock checks passed\n";
}
