"""Run the portable Java core checks; no Android SDK or device is used."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent
MODULE_SOURCES = (
    'music/QueuePlanner.java', 'messaging/SecureChannel.java', 'messaging/MessagePeer.java',
    'messaging/StreamSession.java', 'messaging/LanPeer.java', 'colorvision/ColorVision.java',
    'music/WaveWriter.java', 'music/AudioEditPlan.java', 'music/WavSpeedProcessor.java',
    'music/StereoPcmReader.java', 'colorvision/ComparisonLayout.java',
    'colorvision/PreviewGeometry.java', 'music/AudioFileTags.java', 'music/MusicSpectrum.java',
)
SUITES = ('CoreChecks', 'StreamSessionChecks', 'ColorMusicChecks', 'ColorLayoutChecks',
          'MusicEditingChecks', 'WavSpeedChecks', 'AudioFileTagsChecks', 'MusicAnalysisChecks')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out-dir', type=Path, default=ROOT / 'build/tests')
    args = parser.parse_args()
    compiler = shutil.which('javac')
    if not compiler:
        parser.error('JDK 17 or newer is required; add javac to PATH.')
    java = Path(compiler).resolve().parent / ('java.exe' if os.name == 'nt' else 'java')
    output = args.out_dir.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    sources = []
    for entry in MODULE_SOURCES:
        module, filename = entry.split('/')
        sources.append(ROOT / 'modules' / module / 'org/daidai/modules' / module / filename)
    sources.extend(ROOT / 'tests' / (suite + '.java') for suite in SUITES)
    subprocess.run([compiler, '-encoding', 'UTF-8', '--release', '8', '-d', str(output),
                    *[str(file) for file in sources]], cwd=ROOT, check=True)
    for suite in SUITES:
        command = [str(java), '-cp', str(output), suite]
        if suite == 'WavSpeedChecks':
            command.append(str(output))
        subprocess.run(command, cwd=ROOT, check=True, timeout=75)


if __name__ == '__main__':
    main()
