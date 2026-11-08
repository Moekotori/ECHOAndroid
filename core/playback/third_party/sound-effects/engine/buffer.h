#pragma once

#include <algorithm>
#include <cassert>
#include <cstddef>
#include <stdexcept>
#include <vector>

namespace echo {

/// Planar buffer with an allocation-free active channel view.
/// Data is stored as vector<vector<Sample>> — one inner vector per channel,
/// all channels have the same length (numSamples_).
template <typename Sample>
class PlanarAudioBuffer {
public:
    PlanarAudioBuffer() = default;

    PlanarAudioBuffer(int numChannels, int numSamples) {
        setSize(numChannels, numSamples);
    }

    // ── allocation ──────────────────────────────────────────────────────

    void setSize(int numChannels, int numSamples) {
        if (numChannels < 0 || numSamples < 0) {
            throw std::invalid_argument("FloatAudioBuffer::setSize: negative size");
        }
        channels_.resize(static_cast<std::size_t>(numChannels));
        for (auto& ch : channels_) {
            ch.assign(static_cast<std::size_t>(numSamples), 0.0f);
        }
        numSamples_ = numSamples;
        activeChannels_ = numChannels;
    }

    // ── metadata ────────────────────────────────────────────────────────

    int getNumChannels() const noexcept { return activeChannels_; }
    int getChannelCapacity() const noexcept { return static_cast<int>(channels_.size()); }
    // Changes only the view. Storage is reserved during prepare, never in the callback.
    bool setActiveChannels(int channels) noexcept {
        if (channels < 0 || channels > getChannelCapacity()) return false;
        activeChannels_ = channels; return true;
    }

    int getNumSamples() const noexcept { return numSamples_; }

    // ── raw pointer access ──────────────────────────────────────────────

    /// Returns a writeable pointer to channel[0].
    Sample* getWritePointer(int channel) {
        return channels_[static_cast<std::size_t>(channel)].data();
    }

    /// Returns a read-only pointer to channel[0].
    const Sample* getReadPointer(int channel) const {
        return channels_[static_cast<std::size_t>(channel)].data();
    }

    /// Returns a writeable pointer to channel[startSample].
    Sample* getWritePointer(int channel, int startSample) {
        return channels_[static_cast<std::size_t>(channel)].data() + startSample;
    }

    /// Returns a read-only pointer to channel[startSample].
    const Sample* getReadPointer(int channel, int startSample) const {
        return channels_[static_cast<std::size_t>(channel)].data() + startSample;
    }

    // ── sample get/set ──────────────────────────────────────────────────

    Sample getSample(int channel, int sampleIndex) const {
        return channels_[static_cast<std::size_t>(channel)][static_cast<std::size_t>(sampleIndex)];
    }

    void setSample(int channel, int sampleIndex, Sample value) {
        channels_[static_cast<std::size_t>(channel)][static_cast<std::size_t>(sampleIndex)] = value;
    }

    // ── gain ────────────────────────────────────────────────────────────

    /// Multiply every sample in every channel by `gain`.
    void applyGain(Sample gain) {
        for (auto& ch : channels_) {
            for (auto& s : ch) {
                s *= gain;
            }
        }
    }

    /// Multiply `numSamples` starting at `startSample` on the given channel by `gain`.
    void applyGain(int channel, int startSample, int numSamples, Sample gain) {
        auto& ch = channels_[static_cast<std::size_t>(channel)];
        auto* begin = ch.data() + startSample;
        auto* end = begin + numSamples;
        for (auto* p = begin; p != end; ++p) {
            *p *= gain;
        }
    }

    // ── clear ───────────────────────────────────────────────────────────

    /// Zero every sample in every channel.
    void clear() {
        for (auto& ch : channels_) {
            std::fill(ch.begin(), ch.end(), 0.0f);
        }
    }

    /// Zero `numSamples` starting at `startSample` on **all** channels.
    void clear(int startSample, int numSamples) {
        for (auto& ch : channels_) {
            std::fill_n(ch.begin() + startSample, static_cast<std::size_t>(numSamples), 0.0f);
        }
    }

    /// Zero `numSamples` starting at `startSample` on a specific channel.
    void clear(int channel, int startSample, int numSamples) {
        auto& ch = channels_[static_cast<std::size_t>(channel)];
        std::fill_n(ch.begin() + startSample, static_cast<std::size_t>(numSamples), 0.0f);
    }

    // ── copy ────────────────────────────────────────────────────────────

    /// Copy `numSamples` from source channel to destination channel.
    void copyFrom(int destChannel, int destStartSample,
                  const PlanarAudioBuffer& source,
                  int sourceChannel, int sourceStartSample, int numSamples) {
        auto& dst = channels_[static_cast<std::size_t>(destChannel)];
        const auto& src = source.channels_[static_cast<std::size_t>(sourceChannel)];
        std::copy_n(src.begin() + sourceStartSample, static_cast<std::size_t>(numSamples),
                    dst.begin() + destStartSample);
    }

private:
    std::vector<std::vector<Sample>> channels_;
    int numSamples_ = 0;
    int activeChannels_ = 0;
};

using FloatAudioBuffer = PlanarAudioBuffer<float>;
using DoubleAudioBuffer = PlanarAudioBuffer<double>;

} // namespace echo
