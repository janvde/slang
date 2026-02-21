# Slang Language Syntax Reference

Complete syntax guide for the Slang programming language.

## Table of Contents
- [Variable Declaration](#variable-declaration)
- [Variable Reassignment](#variable-reassignment)
- [Print Statement](#print-statement)
- [Literals](#literals)
- [Arithmetic Operations](#arithmetic-operations)
- [String Operations](#string-operations)
- [Comparison Operators](#comparison-operators)
- [Control Flow](#control-flow)
- [Logical Operators](#logical-operators)
- [Functions](#functions)
- [Lists](#lists)

---

## Variable Declaration

### Immutable Variables (let)
```slang
let x: Int = 10;
let name: String = "hello";
let flag: Bool = true;
let xs: List = [1, 2, 3];
```

Immutable variables cannot be reassigned after declaration.

### Mutable Variables (var)
```slang
var counter: Int = 0;
var temperature: Float = 20.5;
```

Mutable variables can be reassigned to new values.

---

## Variable Reassignment

```slang
var x: Int = 5;
x = 10;        // Allowed (x is mutable)
print(x);      // 10

let y: Int = 5;
y = 10;        // ERROR: Cannot reassign immutable variable 'y'
```

Only variables declared with `var` can be reassigned.

---

## Print Statement

```slang
print(x);
print(name);
print(xs);
```

The `print()` function outputs the value of an expression followed by a newline.

---

## Literals

### Integer Literals
```slang
let a: Int = 42;
let b: Int = -10;
```

### Float Literals
```slang
// Standard decimal notation
let pi: Float = 3.14159;

// Scientific notation
let small: Float = 1.5e-3;   // 0.0015
let large: Float = 2e5;       // 200000.0
```

### Boolean Literals
```slang
let active: Bool = true;
let complete: Bool = false;
```

### String Literals
```slang
let greeting: String = "Hello, World!";

// Escape sequences
let multiline: String = "Line 1\nLine 2";    // Newline
let quoted: String = "He said \"Hi!\"";      // Quote
let path: String = "C:\\Users\\alice";       // Backslash
let tabbed: String = "Col1\tCol2";           // Tab
```

**Supported Escape Sequences:**
- `\n` - Newline
- `\t` - Tab
- `\r` - Carriage return
- `\"` - Double quote
- `\\` - Backslash

---

## Arithmetic Operations

### Basic Operators
```slang
let a: Int = 2 + 3 * 4;   // Result: 14 (respects operator precedence)
let b: Int = 10 / 2;       // Result: 5
let c: Int = 17 % 5;       // Result: 2 (modulo operator)
let d: Int = 10 - 3;       // Result: 7
```

### Float Arithmetic
```slang
let x: Float = 3.5 + 2.1;
let y: Float = 10.0 / 3.0;
```

### Type Promotion (Int → Float)
```slang
// Mixed Int-Float arithmetic automatically promotes Int to Float
let z: Float = 5 + 2.5;   // Result: 7.5 (Int 5 promoted to Float)
```

### Operator Precedence (Highest to Lowest)
1. Literals, identifiers, function calls, parentheses
2. Index operator `[]`
3. Unary NOT `!`
4. Multiplication, Division, Modulo: `*`, `/`, `%`
5. Addition, Subtraction: `+`, `-`
6. Comparison: `>`, `<`, `>=`, `<=`
7. Equality: `==`, `!=`
8. Logical AND: `&&`
9. Logical OR: `||`

---

## String Operations

### String Concatenation
```slang
let greeting: String = "Hello";
let name: String = "Alice";
let message: String = greeting + " " + name;
print(message);  // "Hello Alice"
```

**Note:** String concatenation with `+` operator works in the interpreter but is not yet supported in LLVM code generation (native compilation). Basic string literals and all escape sequences are fully supported in both modes.

---

## Comparison Operators

All comparison operators return `Bool` values:

```slang
if (x > 5) { print(1); }   // Greater than
if (x < 10) { print(2); }  // Less than
if (x >= 10) { print(3); } // Greater than or equal
if (x <= 10) { print(4); } // Less than or equal
if (x == 10) { print(5); } // Equal
if (x != 5) { print(6); }  // Not equal
```

Comparison operators work with both `Int` and `Float` types (with automatic type promotion).

---

## Control Flow

### If/Else Statements

```slang
if (condition) {
    print(1);
} else {
    print(2);
}

// If without else
if (x > 0) {
    print(x);
}

// Nested if statements
if (x > 0) {
    if (y > 0) {
        print(1);
    }
}
```

The condition must evaluate to a `Bool` value.

### While Loops

**Simple While Loop:**
```slang
var i: Int = 0;
while (i < 5) {
    print(i);
    i = i + 1;
}
// Prints: 0, 1, 2, 3, 4
```

**While Loop with Complex Condition:**
```slang
var count: Int = 0;
while (count < 10 && count % 2 == 0) {
    print(count);
    count = count + 1;
}
```

**Nested While Loops:**
```slang
var row: Int = 1;
while (row <= 3) {
    var col: Int = 1;
    while (col <= 3) {
        print(row * 10 + col);
        col = col + 1;
    }
    row = row + 1;
}
// Prints: 11, 12, 13, 21, 22, 23, 31, 32, 33
```

**While Loop Examples:**
- Counter loops
- Sum calculations
- Factorial computation
- Nested iteration
- Conditional termination

---

## Logical Operators

Logical operators only work with `Bool` types and use strict type checking.

### NOT Operator (!)

```slang
let flag: Bool = true;
print(!flag);           // false

let result: Bool = !(10 > 5);  // false

// Double negation
print(!!true);          // true

// In conditionals
if (!is_complete) {
    print(1);
}
```

### AND Operator (&&)

```slang
let a: Bool = true;
let b: Bool = false;
print(a && b);          // false

// Short-circuit evaluation: if left is false, right is not evaluated
if (x > 0 && y < 10) {
    print(1);
}

// Combining with comparisons
if ((x > 5) && (y > 5)) {
    print(2);
}
```

**Short-circuit behavior:**
- If left operand is `false`, the right operand is NOT evaluated
- This prevents unnecessary computation and potential errors

### OR Operator (||)

```slang
let a: Bool = true;
let b: Bool = false;
print(a || b);          // true

// Short-circuit evaluation: if left is true, right is not evaluated
if (x < 0 || x > 100) {
    print(1);
}

// Combining with comparisons
if ((x < 5) || (y > 10)) {
    print(2);
}
```

**Short-circuit behavior:**
- If left operand is `true`, the right operand is NOT evaluated
- Allows safe conditional checks

### Complex Logical Expressions

```slang
// Combining multiple operators
let result: Bool = (a && b) || (!c && d);

// Using parentheses for clarity
let valid: Bool = ((x > 0) && (x < 100)) || (x == -1);

// De Morgan's Laws
let dm1: Bool = !(a && b);      // Equivalent to: (!a || !b)
let dm2: Bool = !(a || b);      // Equivalent to: (!a && !b)
```

### Type Restrictions

```slang
// ✅ Valid - Bool operands
let a: Bool = true && false;

// ❌ Invalid - Int operands not allowed
let b: Bool = 5 && 10;  // ERROR: AND operator requires Bool operands

// ✅ Valid - Comparisons return Bool
let c: Bool = (x > 5) && (y < 10);
```

---

## Functions

### Function Definition

```slang
fn functionName(param1: Type1, param2: Type2): ReturnType {
    // Function body
    return value;
}
```

### Examples

**Simple Function:**
```slang
fn add(a: Int, b: Int): Int {
    return a + b;
}

let sum: Int = add(2, 3);
print(sum);  // 5
```

**Function with Multiple Statements:**
```slang
fn factorial(n: Int): Int {
    var result: Int = 1;
    var i: Int = n;
    while (i > 1) {
        result = result * i;
        i = i - 1;
    }
    return result;
}

let f5: Int = factorial(5);
print(f5);  // 120
```

**Void Functions:**
```slang
fn greet(name: String): Void {
    print(name);
    return;
}

greet("Alice");
```

### Function Rules
- Functions must have explicit return types
- All parameters must have explicit types
- Functions must return a value (unless return type is `Void`)
- Functions can call other functions
- Recursive functions are supported

---

## Lists

### List Literals

```slang
let xs: List = [10, 20, 30];
let empty: List = [];
```

### Indexing

```slang
let xs: List = [10, 20, 30];
print(xs[0]);    // 10
print(xs[1]);    // 20
print(xs[2]);    // 30
```

**Note:** Indices are zero-based.

### Built-in Functions

**Length:**
```slang
let xs: List = [1, 2, 3, 4, 5];
print(len(xs));  // 5
```

### List Examples

```slang
// Create a list
let numbers: List = [1, 2, 3, 4, 5];

// Access elements
let first: Int = numbers[0];
let last: Int = numbers[4];

// Get length
let size: Int = len(numbers);

// Use in loops
var i: Int = 0;
while (i < len(numbers)) {
    print(numbers[i]);
    i = i + 1;
}
```

---

## Complete Example Programs

### Example 1: Variables and Arithmetic
```slang
let x: Int = 10;
let y: Int = 5;
let sum: Int = x + y;
let product: Int = x * y;
print(sum);      // 15
print(product);  // 50
```

### Example 2: Conditionals with Logical Operators
```slang
let age: Int = 20;
let has_license: Bool = true;

if (age >= 18 && has_license) {
    print(1);  // Can drive
} else {
    print(0);  // Cannot drive
}
```

### Example 3: While Loop with Counter
```slang
var i: Int = 1;
var sum: Int = 0;

while (i <= 10) {
    sum = sum + i;
    i = i + 1;
}

print(sum);  // 55 (sum of 1 to 10)
```

### Example 4: Factorial Function
```slang
fn factorial(n: Int): Int {
    var result: Int = 1;
    var i: Int = n;
    while (i > 1) {
        result = result * i;
        i = i - 1;
    }
    return result;
}

print(factorial(5));  // 120
print(factorial(6));  // 720
```

### Example 5: Float Calculations with Type Promotion
```slang
let pi: Float = 3.14159;
let radius: Int = 5;
let area: Float = pi * radius * radius;  // Int promoted to Float
print(area);  // 78.53975
```

### Example 6: List Processing
```slang
let numbers: List = [2, 4, 6, 8, 10];
var i: Int = 0;
var sum: Int = 0;

while (i < len(numbers)) {
    sum = sum + numbers[i];
    i = i + 1;
}

print(sum);  // 30
```

---

## Type System

### Available Types

| Type | Description | Example |
|------|-------------|---------|
| `Int` | 32-bit signed integer | `42`, `-10` |
| `Float` | 32-bit floating point | `3.14`, `2.5e10` |
| `Bool` | Boolean value | `true`, `false` |
| `String` | Text string | `"hello"`, `"world\n"` |
| `List` | List of integers | `[1, 2, 3]` |
| `Void` | No value (functions only) | N/A |

### Type Coercion Rules

**Automatic Promotion:**
- `Int` → `Float` in mixed arithmetic operations
  ```slang
  let result: Float = 5 + 2.5;  // 5 promoted to 5.0
  ```

**No Implicit Conversion:**
- No other implicit conversions
- All types are strictly checked
- Logical operators require `Bool` operands only

---

## Operator Reference

### Arithmetic Operators
| Operator | Description | Example | Result Type |
|----------|-------------|---------|-------------|
| `+` | Addition | `5 + 3` | Int or Float |
| `-` | Subtraction | `10 - 3` | Int or Float |
| `*` | Multiplication | `4 * 5` | Int or Float |
| `/` | Division | `10 / 2` | Int or Float |
| `%` | Modulo | `17 % 5` | Int |

### Comparison Operators
| Operator | Description | Example | Result Type |
|----------|-------------|---------|-------------|
| `>` | Greater than | `x > 5` | Bool |
| `<` | Less than | `x < 10` | Bool |
| `>=` | Greater or equal | `x >= 5` | Bool |
| `<=` | Less or equal | `x <= 10` | Bool |
| `==` | Equal | `x == 5` | Bool |
| `!=` | Not equal | `x != 5` | Bool |

### Logical Operators
| Operator | Description | Example | Result Type |
|----------|-------------|---------|-------------|
| `!` | NOT (negation) | `!flag` | Bool |
| `&&` | AND (conjunction) | `a && b` | Bool |
| `\|\|` | OR (disjunction) | `a \|\| b` | Bool |

**Short-circuit Evaluation:**
- `&&`: If left is false, right is not evaluated
- `||`: If left is true, right is not evaluated

### String Operators
| Operator | Description | Example | Support |
|----------|-------------|---------|---------|
| `+` | Concatenation | `"hi" + " there"` | Interpreter only |

---

## Scoping Rules

### Variable Scope

Variables are scoped to the block in which they are declared:

```slang
let x: Int = 10;  // Global scope

if (true) {
    let y: Int = 5;  // If-block scope
    print(x);         // Can access x
    print(y);         // Can access y
}

print(x);  // Can access x
print(y);  // ERROR: y not in scope
```

### Function Scope

```slang
fn test(param: Int): Int {
    let local: Int = 5;  // Function scope
    return param + local;
}

print(local);  // ERROR: local not in scope
```

### Loop Scope

```slang
var i: Int = 0;
while (i < 3) {
    let temp: Int = i * 2;  // Loop scope
    print(temp);
    i = i + 1;
}

print(temp);  // ERROR: temp not in scope
```

---

## Comments

```slang
// Single-line comments start with //

let x: Int = 42;  // Comments can be at end of line
```

**Note:** Multi-line comments are not currently supported.

---

## Best Practices

### Variable Naming
- Use `let` by default for immutability
- Use `var` only when reassignment is needed
- Use descriptive names: `counter`, `total`, `is_valid`
- Use snake_case: `user_name`, `max_value`

### Type Annotations
- Always provide explicit types (no type inference yet)
- Use appropriate types for your data
- Consider Float for mathematical precision

### Control Flow
- Keep conditions simple and readable
- Use parentheses for complex logical expressions
- Avoid deeply nested loops when possible

### Functions
- Keep functions focused on single tasks
- Use meaningful parameter names
- Document complex logic with comments

---

## Common Patterns

### Counter Loop
```slang
var i: Int = 0;
while (i < n) {
    // Do something
    i = i + 1;
}
```

### Accumulator Pattern
```slang
var sum: Int = 0;
var i: Int = 1;
while (i <= n) {
    sum = sum + i;
    i = i + 1;
}
```

### Conditional Accumulation
```slang
var sum: Int = 0;
var i: Int = 0;
while (i < len(list)) {
    if (list[i] > 0) {
        sum = sum + list[i];
    }
    i = i + 1;
}
```

### Search Pattern
```slang
var found: Bool = false;
var i: Int = 0;
while (i < len(list) && !found) {
    if (list[i] == target) {
        found = true;
    }
    i = i + 1;
}
```

---

## Limitations & Notes

### Current Limitations
- No `for` loops (use `while` instead)
- No `break` or `continue` statements
- No arrays (only integer Lists)
- String concatenation: interpreter only (LLVM codegen pending)
- No string manipulation functions yet
- No type inference (explicit types required)
- Single-line comments only

### Interpreter vs LLVM Differences
- **Interpreter:** Full string concatenation support
- **LLVM/Native:** String concatenation not yet implemented
- All other features work identically in both modes

---

## See Also

- [README.md](README.md) - Main project documentation
- [Example Programs](src/main/resources/) - Working code examples
  - `example.slang` - Basic arithmetic
  - `comparison_demo.slang` - Operators and functions
  - `features_demo.slang` - Core features
  - `logical_operators_demo.slang` - Logical operators
  - `loops_demo.slang` - While loop examples

---

**Last Updated:** February 21, 2026
**Version:** 1.0
**Slang Language Specification**

