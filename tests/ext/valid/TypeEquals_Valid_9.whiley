import * from whiley.lang.*

define bop as {int x, int y} where x > 0
define expr as int|bop

int f(expr e):
    if e is bop:
        return e.x + e.y
    else if e is int:
        return e // requires type difference
    else:
        return -1 // unreachable

void ::main(System sys,[string] args):
    x = f(1)
    sys.out.println(toString(x))
    x = f({x:4,y:10})   
    sys.out.println(toString(x))
