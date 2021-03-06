function contains([int] xs, int x) -> (bool r)
ensures r ==> x in xs:
    //
    int i = 0
    //
    while i < |xs| where i >= 0:
        if xs[i] == x:
            return true
        i = i + 1
    //
    return false

method main(System.Console console):
    [int] ls = [1,2,3,4]
    for l in [3,5,1]:
        bool b = contains(ls,l)
        console.out.println(Int.toString(l) ++ " in " ++ Any.toString(ls) ++ " : " ++ Any.toString(b))
