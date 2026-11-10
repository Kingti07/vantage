#!/usr/bin/env python3
"""Turns failed tests, Checkstyle findings and compiler errors into GitHub annotations.

Annotations of a public repository can be read without signing in (unlike the raw logs), so a failed CI run shows
its cause directly on the run page and through the API. Usage: report-failures.py [gradle output file]
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.getcwd()


def escape(text):
    return text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def relative(path):
    return os.path.relpath(path, ROOT) if path and os.path.isabs(path) else path


count = 0
for report in sorted(glob.glob('**/build/test-results/test/*.xml', recursive=True)):
    for case in ET.parse(report).getroot().iter('testcase'):
        for kind in ('failure', 'error'):
            problem = case.find(kind)
            if problem is not None:
                text = ((problem.get('message') or '') + '\n' + (problem.text or '')[:1500]).strip()
                print(f"::error title=Test {case.get('classname')}.{case.get('name')}::{escape(text)}")
                count += 1

for report in sorted(glob.glob('**/build/reports/checkstyle/*.xml', recursive=True)):
    for file in ET.parse(report).getroot().iter('file'):
        for error in file.iter('error'):
            print(f"::error file={relative(file.get('name'))},line={error.get('line')},title=Checkstyle::"
                  f"{escape(error.get('message', ''))}")
            count += 1

if len(sys.argv) > 1 and os.path.isfile(sys.argv[1]):
    with open(sys.argv[1], encoding='utf-8', errors='replace') as output:
        for line in output:
            if re.search(r'\.java:\d+: (error|warning):', line) or (line.startswith('> ') and not line.startswith('> Task')):
                print(f"::error title=Build::{escape(line.strip())}")
                count += 1

print(f"{count} problem(s) reported", file=sys.stderr)
