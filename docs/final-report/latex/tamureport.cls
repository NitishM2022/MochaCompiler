% Modification based on report class created by Dan Horne
% for UC San Diego report
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{tamureport}[2019/01/12 Report LaTeX class]
\LoadClass[12pt,letterpaper,onecolumn]{article}

% PACKAGE DECLARATIONS
% general page layout and fonts
\usepackage[letterpaper,top=0.85in,bottom=.75in,left=.75in,right=.75in]{geometry}
\usepackage[utf8]{inputenc}
\usepackage[none]{hyphenat}
\usepackage[T1]{fontenc}
\usepackage{parskip}
\usepackage{multicol}

% standard packages
\usepackage{enumerate}
\usepackage[shortlabels]{enumitem}
\usepackage{scrextend}
\usepackage{fancyhdr}
\usepackage{calc} % for calculating minipage widths
% math packages
\usepackage{centernot}
\usepackage{amsthm, amssymb, amsmath,verbatim}
\usepackage{mathtools}
% coding and colors
\usepackage{xifthen}
\usepackage{ifthen}
% color box
\usepackage[most,listings]{tcolorbox}
\usepackage{lmodern}
% graphs and pictures
\usepackage{tikz}
\usepackage{mathrsfs}
\usetikzlibrary{math}
\usetikzlibrary{backgrounds}
\usetikzlibrary{patterns,calc}
\usepackage{graphicx, subcaption}
\usepackage{csvsimple,booktabs}
% \usepackage{filecontents}

%%cache tikz
\usetikzlibrary{external}
\tikzexternalize[prefix=tikz/]

\usepackage{url}
\usepackage{hyperref}
\hypersetup{
    colorlinks=true,
    linkcolor=blue,
    filecolor=magenta,      
    urlcolor=blue,
    pdfpagemode=FullScreen,
    }

\usepackage{titlesec}
\usepackage{xspace}
    
% ARRAY COMMANDS
\usepackage{array}
\newcommand{\PreserveBackslash}[1]{\let\temp=\\#1\let\\=\temp}
\newcolumntype{C}[1]{>{\PreserveBackslash\centering}p{#1}}
\newcolumntype{R}[1]{>{\PreserveBackslash\raggedleft}p{#1}}
\newcolumntype{L}[1]{>{\PreserveBackslash\raggedright}p{#1}}

% HEADER
\pagestyle{fancy}
\fancyhf{}
\renewcommand{\headrulewidth}{0.2pt}
\renewcommand{\footrulewidth}{0.2pt}

\fancyhead[R]{\textsf{\Headauthor}}
\fancyfoot[R]{\quad\textsf{\thepage}
}

\fancypagestyle{plain}{%
\fancyhf{}
\renewcommand{\footrulewidth}{.5pt}
\fancyfoot[L]{\textsf{\Headauthor}}
\fancyfoot[R]{\quad\textsf{\thepage}
} 
}
% DEFINE ENVIRONMENTS

% \titleformat{\section}[block] 
% {\normalfont\bfseries}
% {Section \thesection.}{0.5em}{}


\titleformat
{\section} % command
[hang] % shape: possible values are: hang, block, display, runin, leftmargin, rightmargin, drop, wrap and frame.
{\bfseries\Large} % format
{\thesection.} % label
{0.5em} % sep
{} % before-code
[\normalfont] % after-code
% {
%     % \rule{\textwidth}{1pt}
%     % \vspace{1ex}
%     \centering
% } % before-code
% [
% % \vspace{-0.5ex}%
% % \rule{\textwidth}{0.3pt}
% ] % after-code