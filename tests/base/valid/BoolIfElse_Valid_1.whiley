import * from whiley.lang.*

string f(bool b):
    if(b):
        return "TRUE"
    else:
        return "FALSE"

void ::main(System.Console sys):
    sys.out.println(f(true))
    sys.out.println(f(false))
