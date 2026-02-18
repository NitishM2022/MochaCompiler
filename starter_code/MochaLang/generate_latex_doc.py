#!/usr/bin/env python3
"""
Generate LaTeX document with properly formatted DOT graphs for optimization demonstration.
Uses minimal test cases for clearer, simpler IR graphs.
"""

import os
import re

# Paths
GRAPHS_DIR = "/Users/nitishmalluru/HW/CSCE_434/starter_code/MochaLang/graphs"
RECORDS_DIR = "/Users/nitishmalluru/HW/CSCE_434/starter_code/MochaLang"
SOURCE_DIR = "/Users/nitishmalluru/HW/CSCE_434/starter_code/MochaLang"
OUTPUT_FILE = "/Users/nitishmalluru/.gemini/antigravity/brain/2ad34f07-d4c1-41ab-b0de-e0494973280f/optimization_demonstration.tex"

def split_digraphs(content):
    """Split a DOT file into separate digraph blocks."""
    digraphs = []
    lines = content.split('\n')
    i = 0
    
    while i < len(lines):
        line = lines[i].strip()
        
        if line.startswith('digraph'):
            # Found start of a digraph
            digraph_lines = []
            brace_count = line.count('{') - line.count('}')
            i += 1
            
            # Collect lines until we close all braces
            while i < len(lines) and brace_count > 0:
                current_line = lines[i]
                digraph_lines.append(current_line)
                brace_count += current_line.count('{')
                brace_count -= current_line.count('}')
                i += 1
            
            # Remove the final closing brace line if it's just "}"
            if digraph_lines and digraph_lines[-1].strip() == '}':
                digraph_lines = digraph_lines[:-1]
            
            if digraph_lines:
                digraphs.append('\n'.join(digraph_lines))
        else:
            i += 1
    
    return digraphs

def read_dot_content(filepath):
    """Read DOT file and extract content, handling multiple digraph blocks."""
    with open(filepath, 'r') as f:
        content = f.read()
    
    return split_digraphs(content)

def read_source_code(test_name):
    """Read source code from local directory."""
    source_path = f"{SOURCE_DIR}/{test_name}.mocha"
    try:
        with open(source_path, 'r') as f:
            return f.read()
    except:
        return f"// Source code for {test_name} not accessible"

def read_record(test_name, opt):
    """Read optimization record file."""
    record_path = f"{RECORDS_DIR}/record_{test_name}_{opt}.txt"
    try:
        with open(record_path, 'r') as f:
            content = f.read().strip()
            return content if content else f"// No optimizations recorded for {test_name} {opt}"
    except:
        return f"// Record file for {test_name} {opt} not found"

# Test configurations - using minimal demo tests
tests = [
    {
        'name': 'demo_cp',
        'opt': 'cp',
        'opt_name': 'Constant Propagation (CP)',
        'description': 'Variable x=5 is propagated into the expression x+3.',
        'explanation': 'Constant propagation replaces variable uses with their known constant values. Here, \\texttt{x\\_2} is replaced with \\texttt{5} in the addition operation.'
    },
    {
        'name': 'demo_cf',
        'opt': 'cf',
        'opt_name': 'Constant Folding (CF)',
        'description': 'Algebraic identities simplify expressions.',
        'explanation': 'Constant folding applies algebraic identities: \\texttt{x * 1 → x}, \\texttt{x + 0 → x}, \\texttt{x - x → 0}.'
    },
    {
        'name': 'demo_dce',
        'opt': 'dce',
        'opt_name': 'Dead Code Elimination (DCE)',
        'description': 'Unused assignments are removed.',
        'explanation': 'DCE removes dead code - assignments whose values are never used. The assignment \\texttt{y = 10} is eliminated because y is never read.'
    },
    {
        'name': 'demo_cse',
        'opt': 'cse',
        'opt_name': 'Common Subexpression Elimination (CSE)',
        'description': 'Duplicate computation a+b is reused.',
        'explanation': 'CSE identifies when the same expression (\\texttt{a + b}) is computed multiple times and reuses the first result instead of recomputing.'
    },
    {
        'name': 'demo_cpp',
        'opt': 'cpp',
        'opt_name': 'Copy Propagation (CPP)',
        'description': 'Copy chain a→b→c is propagated.',
        'explanation': 'Copy propagation traces copy chains (\\texttt{b = a; c = b}) and replaces uses with the original value, so \\texttt{c} directly uses \\texttt{a}.'
    },
    {
        'name': 'demo_ofe',
        'opt': 'ofe',
        'opt_name': 'Orphan Function Elimination (OFE)',
        'description': 'Uncalled function is removed.',
        'explanation': 'OFE builds a call graph from \\texttt{main} and removes unreachable functions. The \\texttt{unused} function is never called, so it is eliminated.'
    }
]

# Generate LaTeX document
latex_content = r'''\documentclass[11pt]{article}
\usepackage[margin=0.75in]{geometry}
\usepackage{listings}
\usepackage{xcolor}
\usepackage{graphviz}
\usepackage{caption}
\usepackage{subcaption}

%%% Helper code for build system
\usepackage{xpatch}
\makeatletter
\newcommand*{\addFileDependency}[1]{%
  \typeout{(#1)}
  \@addtofilelist{#1}
  \IfFileExists{#1}{}{\typeout{No file #1.}}
}
\makeatother
\xpretocmd{\digraph}{\addFileDependency{#2.dot}}{}{}

\lstset{
    basicstyle=\ttfamily\small,
    breaklines=true,
    frame=single,
    numbers=left,
    numberstyle=\tiny,
    showstringspaces=false,
    commentstyle=\color{gray},
    keywordstyle=\color{blue}\bfseries,
    stringstyle=\color{red}
}

\title{MochaLang Compiler Optimization Demonstration}
\author{Compiler Optimizations Report}
\date{\today}

\begin{document}

\maketitle

\section{Introduction}

This document demonstrates the effectiveness of various optimizations implemented in the MochaLang compiler. For each optimization, we present:
\begin{itemize}
    \item The source test code
    \item Pre-optimization SSA IR (Control Flow Graph)
    \item Post-optimization SSA IR (Control Flow Graph)
    \item Optimization record showing specific transformations
\end{itemize}

The following optimizations are demonstrated:
\begin{enumerate}
    \item \textbf{CP} - Constant Propagation
    \item \textbf{CF} - Constant Folding (Algebraic Simplification)
    \item \textbf{DCE} - Dead Code Elimination
    \item \textbf{CSE} - Common Subexpression Elimination
    \item \textbf{CPP} - Copy Propagation
    \item \textbf{OFE} - Orphan Function Elimination
\end{enumerate}

\clearpage

'''

# Counter for unique digraph names
digraph_counter = 0
def get_next_name():
    global digraph_counter
    # Generate names: A, B, C, ..., Z, AA, AB, ...
    name = ''
    n = digraph_counter
    while True:
        name = chr(ord('A') + (n % 26)) + name
        n = n // 26
        if n == 0:
            break
        n -= 1
    digraph_counter += 1
    return name

# Generate sections for each test
for i, test in enumerate(tests, 1):
    test_name = test['name']
    opt = test['opt']
    
    # Read files
    source = read_source_code(test_name)
    record = read_record(test_name, opt)
    pre_digraphs = read_dot_content(f"{GRAPHS_DIR}/{test_name}_cfg.dot")
    post_digraphs = read_dot_content(f"{GRAPHS_DIR}/{test_name}_post_{opt}_cfg.dot")
    
    # Create section
    latex_content += f'''\\section{{Test {i}: {test['opt_name']}}}

\\subsection{{Source Code ({test_name}.mocha)}}
\\begin{{lstlisting}}[language=C]
{source}
\\end{{lstlisting}}

\\subsection{{Optimization Record}}
\\begin{{lstlisting}}
{record}
\\end{{lstlisting}}

\\subsection{{IR Graphs}}

\\begin{{figure}}[h!]
\\centering
\\begin{{subfigure}}{{0.48\\textwidth}}
    \\centering
'''
    
    # Add all pre-optimization digraphs
    for digraph_content in pre_digraphs:
        name = get_next_name()
        latex_content += f'''    \\digraph[scale=0.3]{{{name}}}{{
{digraph_content}
    }}
    
'''
    
    latex_content += f'''    \\caption{{Pre-optimization}}
\\end{{subfigure}}
\\hfill
\\begin{{subfigure}}{{0.48\\textwidth}}
    \\centering
'''
    
    # Add all post-optimization digraphs
    for digraph_content in post_digraphs:
        name = get_next_name()
        latex_content += f'''    \\digraph[scale=0.3]{{{name}}}{{
{digraph_content}
    }}
    
'''
    
    latex_content += f'''    \\caption{{Post-{opt.upper()}}}
\\end{{subfigure}}
\\caption{{{test['description']}}}
\\end{{figure}}

\\textbf{{Optimization Explanation:}} {test['explanation']}

\\clearpage

'''

# Add conclusion
latex_content += r'''\section{Conclusion}

The MochaLang compiler successfully implements six key optimizations that significantly improve code quality:

\begin{enumerate}
    \item \textbf{Constant Propagation (CP)} - Replaces variable uses with known constant values
    \item \textbf{Constant Folding (CF)} - Applies algebraic identities to simplify expressions
    \item \textbf{Dead Code Elimination (DCE)} - Removes unreachable and unused code
    \item \textbf{Common Subexpression Elimination (CSE)} - Eliminates redundant computations
    \item \textbf{Copy Propagation (CPP)} - Eliminates unnecessary copy chains
    \item \textbf{Orphan Function Elimination (OFE)} - Removes uncalled functions
\end{enumerate}

These optimizations work synergistically - for example, constant propagation enables constant folding, which in turn can create dead code that is eliminated by DCE. The combination produces efficient, compact machine code from high-level source programs.

\end{document}
'''

# Write output
with open(OUTPUT_FILE, 'w') as f:
    f.write(latex_content)

print(f"LaTeX document generated: {OUTPUT_FILE}")
print(f"Total sections: {len(tests)}")
print(f"Total digraphs: {digraph_counter}")
