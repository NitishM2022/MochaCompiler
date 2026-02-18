import os
import re

def analyze_records():
    files = [f for f in os.listdir('.') if f.startswith('record_') and f.endswith('.txt')]
    files.sort()
    
    missing_optimizations = []
    empty_files = []
    
    opt_keywords = {
        'cp': ['CP:', 'Constant propagated'],
        'cf': ['CF:', 'Folded constant', 'Algebraic simplification'],
        'dce': ['DCE:', 'Eliminated'],
        'cse': ['CSE:', 'Common subexpression elimination'],
        'cpp': ['CPP:', 'Copy propagated'],
        'ofe': ['OFE:', 'Eliminated'],
        'loop': ['Iteration #']
    }
    
    for filename in files:
        # Parse filename: record_TESTNAME_OPT1_OPT2...txt
        # Example: record_test000_cp_cf_dce_loop.txt
        # Example: record_test114_max.txt
        
        parts = filename.replace('record_', '').replace('.txt', '').split('_')
        # The first part(s) are the test name. The rest are opts.
        # But test names can have underscores? e.g. test_arr_param_simple
        # And opts are at the end.
        
        # Heuristic: Opts are known strings.
        known_opts = set(opt_keywords.keys()) | {'max', 'none'}
        
        test_name_parts = []
        opts = []
        
        for part in parts:
            if part in known_opts:
                opts.append(part)
            else:
                # If we already started collecting opts, and see a non-opt, it might be part of test name?
                # But usually opts are at the end.
                # Let's assume once we see an opt, the rest are opts?
                # No, "max" might be a test name? Unlikely.
                if opts:
                    # This is weird. Let's assume opts are strictly at the end.
                    pass
                test_name_parts.append(part)
        
        # Actually, let's split from the right.
        real_opts = []
        real_test_name = ""
        
        # Re-parsing strategy:
        # Split by '_'
        # Check from right to left. If it's an opt, add to opts. Else stop.
        
        parts = filename.replace('record_', '').replace('.txt', '').split('_')
        idx = len(parts) - 1
        while idx >= 0:
            if parts[idx] in known_opts:
                real_opts.append(parts[idx])
                idx -= 1
            else:
                break
        
        real_test_name = "_".join(parts[:idx+1])
        real_opts.reverse()
        
        if not real_opts:
            # Maybe test name is everything?
            real_test_name = "_".join(parts)
        
        with open(filename, 'r') as f:
            content = f.read()
            
        if not content.strip():
            empty_files.append(filename)
            continue
            
        # Check for opts
        for opt in real_opts:
            if opt == 'max':
                # max implies we should see SOME optimization activity, usually loop
                # But specifically it runs cp, cf, dce, cse, cpp...
                # Let's just check if the file is not empty (already done).
                continue
            if opt == 'none':
                continue
                
            keywords = opt_keywords.get(opt, [])
            found = False
            for kw in keywords:
                if kw in content:
                    found = True
                    break
            
            if not found:
                # Special case: maybe the optimization ran but did nothing?
                # But usually record files only contain changes.
                # So if it's missing, it means it did nothing.
                # The user asked: "check all the record files if the optimizations listed are in there"
                # If it's not there, it's a "missing optimization" in the record.
                missing_optimizations.append(f"{filename}: Expected {opt} but not found")

    print("Empty Files:")
    for f in empty_files:
        print(f)
        
    print("\nFiles with missing expected optimizations:")
    for m in missing_optimizations:
        print(m)

if __name__ == "__main__":
    analyze_records()
