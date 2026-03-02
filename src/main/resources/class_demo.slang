// Class demo for Slang (class-lite, no inheritance)

class Point(var x: Int, var y: Int) {
    fn move(dx: Int, dy: Int): Void {
        this.x = this.x + dx;
        this.y = this.y + dy;
        return;
    }

    fn sum(): Int {
        return this.x + this.y;
    }
}

let p: Point = Point(1, 2);
print(p.x);        // 1
print(p.y);        // 2

p.move(3, 4);
print(p.x);        // 4
print(p.y);        // 6
print(p.sum());    // 10

// Reference semantics
let alias: Point = p;
alias.x = 100;
print(p.x);        // 100
