#!/usr/bin/env python3
"""Write the Audacity action list that mixes the taped applause outro. Times are seconds on its clock."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2] / 'voice-lines'
THANKS = next(ROOT.glob('ogg/052_*.ogg'))
VOICE_FILE_AT = 0.66    # the words start 0.12 into the file, so they land at 0.78
STOP_AT = round(VOICE_FILE_AT + 2.09 - 0.1, 3)
STOP_FILE_AT = STOP_AT - 0.065
APPLAUSE_AT, APPLAUSE_SOURCE_AT = 0.2, 0.15
LEAD_TAPE_GAIN = 0.5
CLICK_GAIN, TAPE_GAIN, APPLAUSE_GAIN, VOICE_GAIN, STOP_GAIN = 0.5, 7, 0.5, 1.25, 0.5
TAPE, APPLAUSE, VOICE, STOP = 4, 5, 6, 7


def actions():
    out = []

    def step(tool, **args):
        out.append({'tool': tool, 'args': args})

    def place(source, start, end, track, at, name):
        step('track_select', track=source)
        step('select_region', start=start, end=end)
        step('edit_copy')
        step('track_add_mono')
        step('track_select', track=track)
        step('cursor_set_position', time=at)
        step('edit_paste')
        step('track_set_properties', track=track, name=name, mute=False, gain=0)

    for path in (ROOT / 'sfx/tape_play.wav', ROOT / 'sfx/applause.wav', THANKS, ROOT / 'sfx/tape_stop.wav'):
        step('project_import_audio', path=str(path))
    for track in range(4):
        step('track_mute', track=track, mute=True)
    place(0, 0.085, 0.085 + STOP_AT, TAPE, 0, 'tape')
    place(1, APPLAUSE_SOURCE_AT, APPLAUSE_SOURCE_AT + STOP_AT - APPLAUSE_AT, APPLAUSE, APPLAUSE_AT, 'applause')
    place(2, 0, 2.2478, VOICE, VOICE_FILE_AT, 'thanks')
    place(3, 0, 0.378, STOP, STOP_FILE_AT, 'stop')
    step('track_select', track=TAPE)
    step('select_region', start=0, end=0.3)
    step('effect_amplify', ratio=CLICK_GAIN)
    step('select_region', start=0.3, end=STOP_AT)
    step('effect_amplify', ratio=TAPE_GAIN)
    step('select_region', start=0.3, end=VOICE_FILE_AT + 0.12)
    step('effect_amplify', ratio=LEAD_TAPE_GAIN)
    step('track_select', track=APPLAUSE)
    step('select_region', start=APPLAUSE_AT, end=STOP_AT)
    step('effect_high_pass_filter', frequency=320, rolloff='dB12')
    step('effect_low_pass_filter', frequency=3400, rolloff='dB12')
    step('effect_tremolo', frequency=7, depth=5, waveform=0)
    step('effect_distortion', distortion_type='Soft Overdrive', threshold_db=-12)
    step('effect_amplify', ratio=APPLAUSE_GAIN)
    step('select_region', start=APPLAUSE_AT, end=APPLAUSE_AT + 0.3)
    step('effect_fade_in')
    step('track_select', track=VOICE)
    step('select_region', start=VOICE_FILE_AT, end=VOICE_FILE_AT + 2.2478)
    step('effect_amplify', ratio=VOICE_GAIN)
    step('track_select', track=STOP)
    step('select_region', start=STOP_FILE_AT, end=STOP_FILE_AT + 0.378)
    step('effect_amplify', ratio=STOP_GAIN)
    step('select_all')
    step('project_export_audio', path='{output}/song_outro.wav', num_channels=1)
    return out


if __name__ == '__main__':
    Path(sys.argv[1]).write_text(json.dumps(actions(), indent=1) + '\n')
