// Logical Operators Demo for Slang
// This file demonstrates Phase 3 features: !, &&, ||

// ============================================
// NOT Operator (!)
// ============================================

let flag: Bool = true;
print(!flag);          // false

let is_complete: Bool = false;
print(!is_complete);   // true

// Double NOT
print(!!true);         // true

// ============================================
// AND Operator (&&)
// ============================================

let a: Bool = true;
let b: Bool = true;
print(a && b);         // true

let c: Bool = false;
let d: Bool = false;
print(c && d);         // false

let e: Bool = true;
let f: Bool = false;
print(e && f);         // false

// Short-circuit evaluation: if left is false, right is not evaluated
let x: Bool = false;
let y: Bool = true;
print(x && y);         // false (y not evaluated)

// ============================================
// OR Operator (||)
// ============================================

let g: Bool = true;
let h: Bool = true;
print(g || h);         // true

let i: Bool = false;
let j: Bool = false;
print(i || j);         // false

let k: Bool = true;
let l: Bool = false;
print(k || l);         // true

// Short-circuit evaluation: if left is true, right is not evaluated
let p: Bool = true;
let q: Bool = false;
print(p || q);         // true (q not evaluated)

// ============================================
// Complex Logical Expressions
// ============================================

let result1: Bool = (true && false) || true;
print(result1);         // true

let result2: Bool = !(true && false);
print(result2);         // true

let result3: Bool = (false || false) && true;
print(result3);         // false

// ============================================
// Logical with Comparisons
// ============================================

let n1: Int = 10;
let n2: Int = 5;

if (n1 > 5 && n2 < 10) {
    print(1);           // Prints 1
}

if (n1 < 5 || n2 > 0) {
    print(2);           // Prints 2
}

if (!(n1 < 5)) {
    print(3);           // Prints 3
}

// ============================================
// Practical Examples
// ============================================

let is_admin: Bool = true;
let is_active: Bool = true;

if (is_admin && is_active) {
    print(100);         // Prints 100 (user can access)
}

let user_age: Int = 16;
let has_permission: Bool = false;

if (user_age >= 18 || has_permission) {
    print(200);
} else {
    print(201);         // Prints 201 (not allowed)
}

// Negation in conditions
let is_blocked: Bool = true;

if (!is_blocked) {
    print(300);
} else {
    print(301);         // Prints 301
}

// ============================================
// De Morgan's Laws
// ============================================

// !(a && b) == (!a || !b)
let m1: Bool = true;
let m2: Bool = true;
print(!(m1 && m2));     // false
print((!m1) || (!m2));  // false (same result)

// !(a || b) == (!a && !b)
let m3: Bool = false;
let m4: Bool = false;
print(!(m3 || m4));     // true
print((!m3) && (!m4));  // true (same result)

