define state as (int x, int y) requires x < y
define pState as process state

int pState::send(int x):
    print str(x)
    return -1

void System::main([string] args):
    int x = (spawn (x:1,y:2))->send(1)
    print str(x)
