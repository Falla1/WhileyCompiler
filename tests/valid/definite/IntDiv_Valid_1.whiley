int f(int x, int y) requires y != 0:
    return x / y

void System::main([string] args):
     int x
     x = f(10,2)
     print str(x)  
