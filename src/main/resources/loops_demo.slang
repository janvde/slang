// While Loops Demo for Slang
// This file demonstrates Phase 4 features: while loop constructs

// ============================================
// Simple While Loop
// ============================================

var i: Int = 0;
while (i < 3) {
    print(i);
    i = i + 1;
}

// ============================================
// While Loop with Complex Condition
// ============================================

var count: Int = 1;
while (count <= 5) {
    print(count);
    count = count + 1;
}

// ============================================
// While Loop with If Inside
// ============================================

var j: Int = 0;
while (j < 10) {
    if (j % 2 == 0) {
        print(j);
    }
    j = j + 1;
}

// ============================================
// Nested While Loops
// ============================================

var row: Int = 1;
while (row <= 2) {
    var col: Int = 1;
    while (col <= 3) {
        print(row * 10 + col);
        col = col + 1;
    }
    row = row + 1;
}

// ============================================
// Sum with While Loop
// ============================================

var n: Int = 1;
var total: Int = 0;
while (n <= 10) {
    total = total + n;
    n = n + 1;
}
print(total);           // 55 (sum of 1 to 10)

// ============================================
// Factorial with While Loop
// ============================================

var num: Int = 6;
var fact: Int = 1;
while (num > 1) {
    fact = fact * num;
    num = num - 1;
}
print(fact);            // 720 (6!)

// ============================================
// While with Logical Conditions
// ============================================

var x: Int = 0;
while (x < 20 && x % 2 == 0) {
    print(x);
    x = x + 1;
}

// ============================================
// Countdown While Loop
// ============================================

var countdown: Int = 5;
while (countdown > 0) {
    print(countdown);
    countdown = countdown - 1;
}
print(0);               // Final print after loop

// ============================================
// While Loop Finding Powers
// ============================================

var base: Int = 2;
var power: Int = 1;
while (power < 256) {
    power = power * 2;
}
print(power);           // 256

// ============================================
// While with Float Counter
// ============================================

var fval: Float = 0.0;
while (fval < 3.0) {
    print(fval);
    fval = fval + 1.0;
}

// ============================================
// While Loop with Boolean Condition
// ============================================

let is_running: Bool = true;
var ticks: Int = 0;
while (is_running && ticks < 5) {
    print(ticks);
    ticks = ticks + 1;
}


for (var j: Int = 0; j < 10; j = j + 1) {
    if (j == 4) { continue; }
    if (j == 8) { break; }
    print(j);
}

