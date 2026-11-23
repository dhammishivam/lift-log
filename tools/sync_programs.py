#!/usr/bin/env python3
"""Copies shared/programs.txt into the app (ProgramsData.kt) and the web app (web/index.html),
so both always offer the same programs. Run after editing the catalogue:  python3 tools/sync_programs.py

Format: '#id|Name|days per week|level (beginner/intermediate/advanced/any)|equipment (gym/dumbbell/none)|goal (muscle/strength/fit)|description'
then '@Day name' and one 'Exercise name|sets|reps (seconds for timed exercises)|rest seconds' line per exercise.
"""
import os, re, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
text = open(os.path.join(root, 'shared/programs.txt'), encoding='utf-8').read().strip()
for bad in ('$', '`', '"""', '\\'):
    if bad in text: sys.exit(f'programs.txt must not contain {bad!r}')

kt = os.path.join(root, 'composeApp/src/commonMain/kotlin/app/liftlog/logic/ProgramsData.kt')
open(kt, 'w', encoding='utf-8').write(
    '// Generated from shared/programs.txt by tools/sync_programs.py. Edit that file, not this one.\n'
    'package app.liftlog.logic\n\n'
    'internal const val PROGRAMS_TXT = """' + text + '\n"""\n')

web = os.path.join(root, 'web/index.html')
html = open(web, encoding='utf-8').read()
new, n = re.subn(r'const PROGRAMS_TXT=`[^`]*`;', lambda m: 'const PROGRAMS_TXT=`' + text + '\n`;', html)
if n != 1: sys.exit('web/index.html: expected exactly one PROGRAMS_TXT block')
open(web, 'w', encoding='utf-8').write(new)
print('synced', text.count('\n#') + 1, 'programs')
