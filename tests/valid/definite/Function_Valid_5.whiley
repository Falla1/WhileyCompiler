define fr5nat as int requires $ >= 0

{fr5nat} g({fr5nat} xs):
    return { y | y in xs, y > 1 }

void f({fr5nat} x):
    print str(x)

void System::main([string] args):
    {fr5nat} ys = {1,2,3}
    f(g(ys))
