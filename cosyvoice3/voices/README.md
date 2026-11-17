# Voice reference

This repository includes two reference clips:

- `stella.wav`: a neutral weather-forecast reference generated locally for the
  Stella voice, 48 kHz mono, trimmed to 6.15 s.
- `tide.wav`: the male Tide reference, 44.1 kHz stereo, trimmed to 6.4 s.

Other voice clips and backups are ignored by the root `.gitignore`. The original intimate Stella clip is kept only locally as `ostella.wav` and is not published. If `stella.wav` is absent, the adapter falls back to the official `repo/asset/zero_shot_prompt.wav` and its official transcript.

`tide.wav` is an optional local male reference used by the `tide` voice. It is not committed; when absent, GPT-SoVITS falls back to the Stella reference and CosyVoice falls back to `repo/asset/cross_lingual_prompt.wav`.
