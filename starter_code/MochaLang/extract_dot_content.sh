#!/bin/bash

# Extract DOT graph content (without digraph G { ... } wrapper) for LaTeX embedding

extract_dot_content() {
    local input_file="$1"
    # Remove first line (digraph G {), last two lines (} and empty line), and trim
    sed '1d;$d;$d' "$input_file" | sed 's/^[[:space:]]*//'
}

cd /Users/nitishmalluru/HW/CSCE_434/starter_code/MochaLang

echo "Extracting DOT content for LaTeX..."

# Test 1: CP
extract_dot_content "graphs/test000_cfg.dot" > "graphs/test000_pre_content.txt"
extract_dot_content "graphs/test000_post_cp_cfg.dot" > "graphs/test000_post_content.txt"

# Test 2: CF  
extract_dot_content "graphs/test209-cf_cfg.dot" > "graphs/test209_pre_content.txt"
extract_dot_content "graphs/test209-cf_post_cf_cfg.dot" > "graphs/test209_post_content.txt"

# Test 3: DCE
extract_dot_content "graphs/test009_cfg.dot" > "graphs/test009_pre_content.txt"
extract_dot_content "graphs/test009_post_dce_cfg.dot" > "graphs/test009_post_content.txt"

# Test 4: CSE
extract_dot_content "graphs/test113_cfg.dot" > "graphs/test113_pre_content.txt"
extract_dot_content "graphs/test113_post_cse_cfg.dot" > "graphs/test113_post_content.txt"

# Test 5: CPP
extract_dot_content "graphs/test202_cfg.dot" > "graphs/test202_pre_content.txt"
extract_dot_content "graphs/test202_post_cpp_cfg.dot" > "graphs/test202_post_content.txt"

# Test 6: OFE
extract_dot_content "graphs/test206-ofe_cfg.dot" > "graphs/test206_pre_content.txt"
extract_dot_content "graphs/test206-ofe_post_ofe_cfg.dot" > "graphs/test206_post_content.txt"

echo "Done! Content extracted to *_content.txt files"
ls -lh graphs/*_content.txt
