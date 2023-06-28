class Pair implements Comparable<Pair>{
    
    final int x;
    final int y;
    Pair(int x, int y) {this.x=x;this.y=y;}

    public int getX() {
        return x;
      }

    public int getY() {
        return y;
      }
    // depending on your use case, equals? hashCode?  More methods?
    public int compareTo( Pair Pair2) {
        if(getX() == Pair2.getX() && getY() == Pair2.getY()) return 0;
 
        else if(getX() < Pair2.getX()) return -1;

        else if(getX() > Pair2.getX()) return 1;

        else if(getY() < Pair2.getY()) return -1;

        else return 1;
    }
  }