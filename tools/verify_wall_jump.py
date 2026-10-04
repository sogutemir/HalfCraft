"""Live regression: load captured wall/dirt trap, jump into wall, then walk away."""
import json
import threading
import time
from pathlib import Path
from physics_status import status
from window import run


def command(text):
    run('HalfCraft Bridge', ['open', text, 'close', 'wait:.3'])


def sample_actions(actions):
    errors = []
    def act():
        try:
            run('HalfCraft Bridge', actions)
        except Exception as error:
            errors.append(error)
    worker = threading.Thread(target=act)
    worker.start()
    samples = []
    while worker.is_alive():
        samples.append(status())
        time.sleep(.05)
    worker.join()
    if errors:
        raise errors[0]
    return samples


def test():
    command('load hc_wall_stuck')
    time.sleep(4)
    command('hc_physics 1')
    command('hc_input 1')
    time.sleep(3)
    command('hc_look 0 -90')
    start = status()
    assert start['host']['enabled'] and start['guest']['active'], start
    idle = sample_actions(['wait:3'])
    assert idle[-1]['host']['correction']-idle[0]['host']['correction'] <= 2, idle
    jump = sample_actions(['hold:W+SPACE:.5', 'wait:2'])
    height = max(s['guest']['feet'][1] for s in jump)-start['guest']['feet'][1]
    assert height > .7, (height, jump)
    settled = sample_actions(['wait:3'])
    assert settled[-1]['host']['correction']-settled[0]['host']['correction'] <= 2, settled
    command('hc_look 0 90')
    before = status()
    away = sample_actions(['hold:A:.25', 'hold:W:.7', 'wait:1'])
    end = away[-1]
    distance = before['guest']['feet'][2]-end['guest']['feet'][2]
    assert distance > .5, (before, end)
    assert all(s['host']['enabled'] and s['guest']['active'] for s in idle+jump+settled+away)
    result = dict(result='PASS', start=start, jump_height=height, escape_distance=distance,
                  corrections_after_jump=jump[-1]['host']['correction'], end=end)
    (Path(__file__).resolve().parents[1]/'local/wall-jump-results.json').write_text(
        json.dumps(result, indent=2)+'\n', encoding='utf8')
    print(json.dumps(result))


if __name__ == '__main__':
    test()
