import whiley.lang.System

type cr2num is (int x) where x in {1, 2, 3, 4}

function f(cr2num x) -> string:
    int y = x
    return Any.toString(y)

method main(System.Console sys) -> void:
    sys.out.println(f(3))
