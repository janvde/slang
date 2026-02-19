// Comparison operators demonstration

let x: Int = 10;
let y: Int = 5;
let z: Int = 5;

// Arithmetic with precedence
let a: Int = 2 + 3 * 4;
print(a);

// Greater than
if (x > y) {
    print(1);
}

// Less than
if (y < x) {
    print(2);
}

// Equal
if (y == z) {
    print(3);
}

// Not equal
if (x != y) {
    print(4);
}

// Greater than or equal
if (x >= y) {
    print(5);
}

if (y >= z) {
    print(6);
}

// Less than or equal
if (y <= x) {
    print(7);
}

if (z <= y) {
    print(8);
}

// Complex expressions with comparisons
let result: Int = (5 + 5) * 2;
if (result == 20) {
    print(100);
}

fn add(a: Int, b: Int): Int {
    return a + b;
}

let x: Int = add(2, 3);
print(x);

let xs: List = [1, 2, 3];
print(xs);
