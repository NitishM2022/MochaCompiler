package types;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TypeList extends Type implements Iterable<Type> {

    private List<Type> list;

    public TypeList () {
        list = new ArrayList<>();
    }

    public void append (Type type) {
        list.add(type);
    }

    public List<Type> getList () {
        return list;
    }

    @Override
    public Iterator<Type> iterator () {
        return list.iterator();
    }

    //TODO more helper here
    @Override
    public boolean equivalent(Type other) {
        if (other instanceof TypeList) {
            TypeList otherList = (TypeList) other;
            if (this.list.size() != otherList.list.size()) {
                return false;
            }
            for (int i = 0; i < this.list.size(); i++) {
                if (!this.list.get(i).equivalent(otherList.list.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "TypeList" + list.toString();
    }
    
}
