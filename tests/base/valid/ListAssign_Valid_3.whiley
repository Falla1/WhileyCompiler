import whiley.lang.*:*

string f(int i):
    arr1 = [1,2,64]
    arr2 = arr1
    if i != |arr1|:
        arr2[2] = 3
    else:
        arr2[2] = i
    assert arr2[2] == |arr1|
    return str(arr1) + str(arr2)

void ::main(System sys,[string] args):
    sys.out.println(f(2))
    sys.out.println(f(3))
