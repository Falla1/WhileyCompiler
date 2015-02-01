function divide(real lhs, int rhs) -> real
requires rhs > 0:
    //
    real tmp = (real) rhs
    //
    return lhs / tmp

method main(System.Console console):
    console.out.println("10.0 / 2 = " ++ Real.toString(divide(10.0,2)))
    console.out.println("10.0 / 3 = " ++ Real.toString(divide(10.0,3)))
