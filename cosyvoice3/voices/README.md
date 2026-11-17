# Voice reference

This private repository includes the current `stella.wav` reference clip:

- mono
- 44.1 kHz source, trimmed to about 10 seconds
- clean, without long silence or background music

Other voice clips and backups are ignored by the root `.gitignore`. If `stella.wav` is absent, the adapter falls back to the official `repo/asset/zero_shot_prompt.wav` and its official transcript.

`tide.wav` is an optional local male reference used by the `tide` voice. It is not committed; when absent, GPT-SoVITS falls back to the Stella reference and CosyVoice falls back to `repo/asset/cross_lingual_prompt.wav`.
