import whiley.lang.*:*

define pos as int
define neg as int
define expr as pos|neg|[int]

string f(expr e):
    if e is pos && e > 0:
        return "POSITIVE: " + str(e)
    else:
        return "NEGATIVE: " + str(e)

void ::main(System sys,[string] args):
    sys.out.println(f(-1))
    sys.out.println(f(1))
    sys.out.println(f(1234))
 
