// Comprehensive Feature Demo for Slang
// This file demonstrates all features added in Phases 0-2

// ============================================
// Phase 0: Modulo Operator
// ============================================
print(10 % 3);    // 1
print(17 % 5);    // 2
print(20 % 4);    // 0

// ============================================
// Phase 1.1: Boolean Literals
// ============================================
let is_active: Bool = true;
let is_complete: Bool = false;

print(is_active);   // true
print(is_complete); // false

if (true) {
    print(100);     // Prints 100
}

if (false) {
    print(200);     // Won't print
} else {
    print(300);     // Prints 300
}

// ============================================
// Phase 1.2: Float Literals
// ============================================
let pi: Float = 3.14159;
let radius: Float = 2.5;
let area: Float = pi * radius * radius;

print(pi);          // 3.14159
print(radius);      // 2.5
print(area);        // 19.634...

// Float arithmetic
let sum: Float = 1.5 + 2.5;
print(sum);         // 4.0

let difference: Float = 10.5 - 3.2;
print(difference);  // 7.3

let product: Float = 2.5 * 4.0;
print(product);     // 10.0

let quotient: Float = 15.0 / 3.0;
print(quotient);    // 5.0

// Int-Float Type Promotion (mixed operations)
let int_val: Int = 5;
let float_val: Float = 2.5;
let mixed_result: Float = int_val + float_val;
print(mixed_result); // 7.5

// Float comparisons
if (pi > 3.0) {
    print(1);       // Prints 1
}

if (radius < 3.0) {
    print(2);       // Prints 2
}

if (10.5 >= 10.0) {
    print(3);       // Prints 3
}

// ============================================
// Phase 1.3: String Literals
// ============================================
let greeting: String = "Hello, World!";
print(greeting);    // Hello, World!

let name: String = "Alice";
let message: String = "Welcome";

// Note: String concatenation works in interpreter but requires proper
// string handling in LLVM code generation (future enhancement)

// Escape sequences
let with_newline: String = "Line 1\nLine 2";
print(with_newline); // Prints with newline

let with_tab: String = "Column1\tColumn2";
print(with_tab);     // Prints with tab

let with_quote: String = "She said \"Hello\"";
print(with_quote);   // She said "Hello"

let with_backslash: String = "Path: C:\\Users\\alice";
print(with_backslash); // Path: C:\Users\alice

// ============================================
// Phase 2: Variable Mutability
// ============================================

// Immutable variables (let)
let constant: Int = 42;
print(constant);    // 42

// Note: This would cause an error if uncommented:
// constant = 100;  // ERROR: Cannot reassign immutable variable 'constant'

// Mutable variables (var)
var counter: Int = 0;
print(counter);     // 0

counter = 1;
print(counter);     // 1

counter = 2;
print(counter);     // 2

counter = 3;
print(counter);     // 3

// Variable reassignment with expressions
var x: Int = 10;
var y: Int = 5;

x = y + 3;
print(x);           // 8

y = x * 2;
print(y);           // 16

// Mutable float variables
var temperature: Float = 20.5;
temperature = 21.0;
temperature = 19.5;
print(temperature); // 19.5

// Mutable string variables
var text: String = "Initial";
text = "Updated";
// Note: String concatenation (text = text + " again") works in interpreter
// but not yet supported in LLVM code generation
print(text);        // Updated

// ============================================
// Combined Features Demo
// ============================================

// Using multiple features together
var score: Int = 0;
let max_score: Int = 100;
let threshold: Float = 75.5;

if (score < max_score) {
    score = score + 10;
    print(score);   // 10
}

// Complex expressions with mixed types
var total: Float = 0.0;
let num1: Int = 5;
let num2: Float = 2.5;
let num3: Int = 3;

total = num1 + num2 + num3;
print(total);       // 10.5

// String operations (interpreter only - concatenation not yet in LLVM)
var output: String = "Result: ";
print(output);      // Result:

// Boolean results from comparisons
let is_valid: Bool = 10 > 5;
print(is_valid);    // true

// List operations still work
let numbers: List = [1, 2, 3, 4, 5];
print(numbers);     // [1, 2, 3, 4, 5]
print(numbers[2]);  // 3
print(len(numbers)); // 5

// Function calls
fn add(a: Int, b: Int): Int {
    return a + b;
}

let result: Int = add(5, 3);
print(result);      // 8




