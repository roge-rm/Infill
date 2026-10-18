The desktop device backend uses the same synth as Android, with miniaudio
0.11.22 (copied from Apogee) opening PulseAudio or ALSA at runtime.
`miniaudio/miniaudio.h` includes its MIT-0 / public-domain licence.

Music is decoded by stb_vorbis, from nothings/stb commit
2c980bb59875b0d32144a71867fbdebb2f77cd20. `stb_vorbis.c` includes its MIT /
public-domain licence. Both vendored sources are built offline.
