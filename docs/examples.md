# Compiled Examples

These examples demonstrate the MochaLang compiler's optimization and code generation capabilities by showing source snippets alongside their compiled DLX assembly outputs.

## Example 1: Constant Folding & Dead Branch Elimination

This test focuses on how the compiler handles nested conditionals when the conditions are statically determinable.

### Source Code (`tests/test008.txt`)

```text
main
int a, b, c, d, e;
{
    a = 1;
    b = a + 1;
    c = b + 1;
    d = c + 1;
    e = d * 2;
    if ( a > b )
    then
      if (c > d)
      then
        call printInt(c);
      else
        call printInt(d);
      fi;
    else // taken branch, b is 2; e is 8
      if (b > e)
      then
        call printInt(b);
      else
        call printInt(e); // 8
      fi;
    fi;
}.
```

### Compiled DLX Assembly

After SSA conversion, Constant Propagation, Constant Folding, and Dead Code Elimination, the compiler erases all the unreachable branches and intermediate mathematical noise. It simply materializes the final expected value (`8`) into a register and prints it.

```text
0:	ADDI 29 30 -4000
1:	JSR 12
2:	RET 0
3:	PSH 31 29 -4
4:	PSH 28 29 -4
5:	ADD 28 0 29
6:	BEQ 0 1
7:	BEQ 0 1
8:	ADDI 25 0 8
9:	WRI 25
10:	BEQ 0 1
11:	ADDI 1 0 0
12:	BEQ 0 1
13:	ADD 29 28 0
14:	POP 28 29 4
15:	POP 31 29 4
16:	RET 31
```

---

## Example 2: Common Subexpression and Register Coloring

This test mixes runtime I/O with static algebraic chains to test how cleanly the compiler intertwines knowns with unknowns.

### Source Code (`tests/test010.txt`)

```text
main
int a, b, c, d;
{
  a = call readInt();
  b = call readInt();

  c = b * 9 + a - 1;
  b = b * 3;
  d = 4 + a;
  a = 3 + 4;
  b = 3 + 5 * 8;

  call printInt( b ); // 43
  call printInt( d ); // 4 + a
}.
```

### Compiled DLX Assembly

The `RDI` instruction queries the user. Notice how the calculation of `4 + a` maps to a direct `ADDI 2 3 4`. The print computation for `b` evaluates statically to `43`, loaded straight into `R25`. Dead assignments like `c` are completely pruned out.

```text
0:	ADDI 29 30 -4000
1:	JSR 12
2:	RET 0
3:	PSH 31 29 -4
4:	PSH 28 29 -4
5:	ADD 28 0 29
6:	RDI 3
7:	ADDI 2 3 4
8:	ADDI 25 0 43
9:	WRI 25
10:	WRI 2
11:	ADD 29 28 0
12:	POP 28 29 4
13:	POP 31 29 4
14:	RET 31
```
