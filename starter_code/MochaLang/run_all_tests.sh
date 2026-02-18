#!/bin/bash

# Script to run all PA6 test cases based on tests.meta AND unlisted tests
# Usage: ./run_all_tests.sh

WORKING_DIR="/Users/nitishmalluru/HW/CSCE_434"
PA6_DIR="$WORKING_DIR/PA6"
COMPILER_DIR="$WORKING_DIR/starter_code/MochaLang"
TESTS_META="$PA6_DIR/tests.meta"
RESULTS_FILE="$COMPILER_DIR/test_results.txt"

# Change to compiler directory
cd "$COMPILER_DIR" || exit 1

# Clear previous results
> "$RESULTS_FILE"

echo "========================================" >> "$RESULTS_FILE"
echo "Running All PA6 Tests" >> "$RESULTS_FILE"
echo "Started at: $(date)" >> "$RESULTS_FILE"
echo "========================================" >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

echo "Tests running... Output is being written to $RESULTS_FILE"

# Counters
total_tests=0
passed_tests=0
failed_tests=0

# Track seen tests to avoid duplicates
seen_tests=" "

# Function to run a single test
run_test() {
    local test_name=$1
    local opts=$2
    local expected_output=$3
    
    total_tests=$((total_tests + 1))
    
    # Construct test file path
    local test_file="$PA6_DIR/${test_name}.txt"
    
    # Check if test file exists
    if [[ ! -f "$test_file" ]]; then
        echo "❌ Test $test_name: FILE NOT FOUND" >> "$RESULTS_FILE"
        failed_tests=$((failed_tests + 1))
        return
    fi
    
    # Determine input file
    local input_file="$PA6_DIR/${test_name}.in"
    if [[ ! -f "$input_file" ]]; then
        input_file="$PA6_DIR/dummy.in"
    fi
    
    # Build optimization flags
    local opt_flags=""
    if [[ -n "$opts" ]]; then
        for opt in $opts; do
            case "$opt" in
                "loop")
                    opt_flags="$opt_flags -loop"
                    ;;
                "max")
                    opt_flags="$opt_flags -max"
                    ;;
                *)
                    opt_flags="$opt_flags -o $opt"
                    ;;
            esac
        done
    fi
    
    # Run the test
    echo "Running: $test_name with opts: [$opts]" >> "$RESULTS_FILE"
    
    # Always compile to ensure changes are picked up (only once per run would be better, but this is safer)
    # Suppress output to keep logs clean
    javac -d build/classes -cp "../../lib/commons-cli-1.9.0.jar" -sourcepath src src/mocha/CompilerTester.java 2>&1 | grep -v "Note:" > /dev/null
    
    # Execute compiler in background with timeout (10 seconds)
    java -cp "build/classes:../../lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s "$test_file" -b -i "$input_file" $opt_flags > /tmp/test_output_$$.txt 2>&1 &
    local java_pid=$!
    
    # Wait for process with timeout
    local timeout_counter=0
    while kill -0 $java_pid 2>/dev/null && [ $timeout_counter -lt 10 ]; do
        sleep 1
        timeout_counter=$((timeout_counter + 1))
    done
    
    local exit_code=0
    # Check if process is still running (timeout occurred)
    if kill -0 $java_pid 2>/dev/null; then
        kill -9 $java_pid 2>/dev/null
        wait $java_pid 2>/dev/null
        exit_code=124  # Timeout exit code
    else
        wait $java_pid
        exit_code=$?
    fi
    
    local output=$(cat /tmp/test_output_$$.txt 2>/dev/null || echo "")
    rm -f /tmp/test_output_$$.txt
    
    # Check result
    if [[ $exit_code -eq 124 ]]; then
        # Timeout occurred
        echo "⏱️  TIMEOUT: Test exceeded 10 seconds" >> "$RESULTS_FILE"
        failed_tests=$((failed_tests + 1))
    elif [[ $exit_code -eq 0 ]]; then
        echo "$output" >> "$RESULTS_FILE"
        passed_tests=$((passed_tests + 1))
    else
        echo "❌ ERROR: Compilation/execution failed" >> "$RESULTS_FILE"
        echo "$output" >> "$RESULTS_FILE"
        failed_tests=$((failed_tests + 1))
    fi
    
    echo "" >> "$RESULTS_FILE"
}

# 1. Run tests from tests.meta
echo "--- Running tests from tests.meta ---" >> "$RESULTS_FILE"
while IFS= read -r line; do
    # Skip empty lines
    [[ -z "$line" ]] && continue
    
    # Parse the line: test_name, opts, expected_output
    IFS=',' read -r test_name opts expected_output <<< "$line"
    
    # Trim whitespace
    test_name=$(echo "$test_name" | xargs)
    opts=$(echo "$opts" | xargs)
    expected_output=$(echo "$expected_output" | xargs)
    
    # Add to seen tests
    seen_tests="$seen_tests$test_name "
    
    run_test "$test_name" "$opts" "$expected_output"
    
done < "$TESTS_META"

# 2. Run remaining tests found in directory
echo "--- Running remaining unlisted tests (No Optimizations) ---" >> "$RESULTS_FILE"

# Find all test files
for test_file in "$PA6_DIR"/test*.txt; do
    # Get basename
    filename=$(basename "$test_file")
    
    # Skip _asm files
    if [[ "$filename" == *"_asm.txt" ]]; then
        continue
    fi
    
    # Get test name (remove .txt)
    test_name="${filename%.txt}"
    
    # Check if already run (check if " test_name " is in seen_tests)
    if [[ "$seen_tests" == *" $test_name "* ]]; then
        continue
    fi
    
    run_test "$test_name" "" ""
done

# Summary
echo "========================================" >> "$RESULTS_FILE"
echo "Test Summary" >> "$RESULTS_FILE"
echo "========================================" >> "$RESULTS_FILE"
echo "Total Tests:  $total_tests" >> "$RESULTS_FILE"
echo "Passed:       $passed_tests" >> "$RESULTS_FILE"
echo "Failed:       $failed_tests" >> "$RESULTS_FILE"
if [[ $total_tests -gt 0 ]]; then
    success_rate=$(echo "scale=1; ($passed_tests * 100) / $total_tests" | bc)
    echo "Success Rate: ${success_rate}%" >> "$RESULTS_FILE"
else
    echo "Success Rate: 0.0%"  >> "$RESULTS_FILE"
fi
echo "========================================" >> "$RESULTS_FILE"
echo "Finished at: $(date)" >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"
echo "Results saved to: $RESULTS_FILE" >> "$RESULTS_FILE"
