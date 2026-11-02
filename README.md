# Audio Regulator / Wave Studio

A Java desktop recording application for Fedora KDE / PipeWire. It records **real input audio**, displays waveforms derived from the captured PCM samples, and plays the timeline through selected output devices.

## Build and run

Install a JDK 25. The Gradle wrapper downloads the pinned Gradle version automatically; no global Gradle installation or custom build scripts are needed.

```bash
./gradlew run
./gradlew build
./gradlew installDist
```

`build` runs unit tests and produces the application distribution in `build/distributions/`. `installDist` creates a runnable installation including its dependencies:

```bash
./build/install/audio-regulator/bin/audio-regulator
```

Import this directory as a Gradle project in IntelliJ IDEA, Eclipse, or another Java IDE. The entry point is `studio.wave.WaveStudio`.

On Fedora, the native audio tools are provided by:

```bash
sudo dnf install java-25-openjdk-devel pulseaudio-utils
```

`pactl`, `parec`, and `pacat` connect to the running PipeWire PulseAudio service. This uses Fedora's existing audio server; it does not replace PipeWire or change global default devices. FFmpeg is used to import formats other than native 48 kHz, 16-bit mono PCM WAV. Your system already has these tools.

## Recording and playback

1. Use **+** beside **Inputs** to select microphones or other capture inputs. Speaker-monitor sources are excluded. The system's default input is initially selected when available.
2. Use **+** beside **Outputs** to select playback destinations. Only sinks (speakers, headphones, audio interfaces) appear here. The system's default output is initially selected when available.
3. Choose a recording folder. Press **Record** to capture each selected input into its own WAV file. Press **Stop** to finalize the headers. Audio is streamed to disk during recording, and remains in the folder even if you do not press Save session.
4. The timeline waveform and input meters come from actual PCM samples. Silence stays flat. Device failures produce an error and preserve any audio already captured; there is no synthetic fallback.
5. Press **Play** to mix the timeline into the selected outputs. Track offsets, seeking, and mute state affect actual playback. Multiple outputs receive the same mix. Recording and playback are mutually exclusive to avoid unintended feedback.
6. **Save session** writes JSON metadata with real WAV references, selected inputs/outputs, offsets, and mute states. Imported audio outside the selected folder is copied into it, preserving the originals. Each save gets a unique filename. Session-JSON reopening is not currently implemented; existing WAV recordings can be added from the folder browser.

All captured audio uses **48 kHz, 16-bit PCM, mono**, one file per selected input. Stereo input devices are downmixed for voice recording. Independent hardware devices have their own clocks; the application does not apply studio-grade latency/drift compensation.

Click an audio file in the library to load it into the timeline. Supported formats are decoded by FFmpeg when needed, with converted PCM stored in an application-owned `.wave-audio/` cache beside the original. Original recordings are never overwritten. The library is refreshed after recording; use Refresh for external folder changes.

Keyboard shortcuts: **R** record/stop, **Space** play/pause, **Home** return to start, **Ctrl+S** save session, **Ctrl+O** choose folder. Click/drag the timeline to seek, use **Left/Right** for finer steps, and **Shift+scroll** to pan.

## Voice Impact Coach

Open a recording folder and click **Voice Impact Coach…** in the Recordings panel. Choose a microphone take and click **Analyze delivery** for an editable English transcript, word timings in the service result, pace, filler counts, pauses, pitch and energy range. The local iamspeaker service runs on CPU.

With the EditX service running on an NVIDIA GPU, review the clip transcript, choose a style and click **Generate enhanced reference**. Use **Compare recordings** to listen to both versions and see whether the recognized words changed. For recordings longer than 30 seconds, choose a start and length and analyze that section before generating a reference.

[Local service setup, GPU fine-tuning and verification](integrations/README.md)

## Project structure

```text
audio_regulator/
├── build.gradle                  # Java/application plugins, dependencies, verification tasks
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat          # Standard Gradle wrapper
├── gradle/wrapper/
├── src/main/java/studio/wave/
│   ├── WaveStudio.java           # Small application entry point
│   ├── app/                      # Session/transport coordination and background operations
│   ├── audio/                    # Device discovery, capture, WAV I/O, mixing, playback
│   ├── model/                    # Devices, tracks, session state, sampled waveform envelope
│   ├── storage/                  # Recording library, decoding, session persistence
│   └── ui/                       # Window, theme, transport, header
│       ├── components/           # Reusable buttons, icons, panels
│       ├── devices/              # Input/output sidebar and two-column picker
│       ├── library/              # Folder browser and recording cards
│       └── timeline/             # Editor and sample-derived waveform rendering
└── src/test/java/studio/wave/     # Unit, real-audio integration, desktop UI tests
```

Generated files belong under `build/`. Your existing `demo_recordings/` folder is left intact and ignored by Git.

## Verification

```bash
./gradlew test                    # Headless unit tests; no microphone access
./gradlew audioIntegrationTest    # Real PipeWire round trip using temporary virtual sinks
./gradlew uiTest                  # Swing interaction/layout checks; requires a desktop session
```

The audio integration test plays a known signal into an isolated virtual sink, captures it through the audio server, verifies its frequency and WAV data, and verifies a second input stays silent. It also checks missing inputs/outputs and capture-device disconnection. It does not play through physical speakers or record your microphone; its temporary audio modules are unloaded afterward. The UI test creates screenshots under `build/screenshots/`.

Build conventions follow the [Gradle application plugin](https://docs.gradle.org/current/userguide/application_plugin.html). Explicit device routing uses PipeWire's [no-fallback/no-reconnect stream properties](https://docs.pipewire.org/page_man_pipewire-props_7.html), and the application verifies the connected source/sink before accepting the stream.
