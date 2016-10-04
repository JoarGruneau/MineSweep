
import java.util.ArrayList;



/* Copyright (C) 1995 John D. Ramsdell

This file is part of Programmer's Minesweeper (PGMS).

PGMS is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

PGMS is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with PGMS; see the file COPYING.  If not, write to
the Free Software Foundation, 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.
*/

/**
 * The class SinglePointStrategy implements a PGMS strategy.
 * The Single Point Strategy makes a decision based on information
 * available from a single probed point in the mine map. <p>
 * The strategy looks at a probed point.  If the number of mines
 * near the point equals the number of marks near the point, the
 * strategy infers that near points whose status is unknown do not
 * contain mines.  Similarly, if the number of mines near the point
 * equals the number of marks near the point plus the number of
 * unknowns near, the strategy infers that the near points whose status
 * is unknown contain mines.
 * <p> The implementation makes extensive use of sets.
 * @see Strategy
 * @see set.Set
 * @version October 1995
 * @author John D. Ramsdell
 */
public final class OurStrategy implements Strategy {
    @Override
    public void play(Map m) {
   }
    
    
    
    /**
 * Loops through the constraints and checks if they are satisfied: 
 * @param vars as ArrayList
 * @param constraints ArrayList
 * @param sum ArrayList
 * @return boolean
 */
    public boolean meetsConstraints(ArrayList<Integer> vars, 
            ArrayList<ArrayList<Integer>> constraints, ArrayList<Integer> sum){
        int tmpSum;
        for(int index=0;index<constraints.size();index++){
            tmpSum=0;
            for(int var:constraints.get(index)){
                tmpSum+=vars.get(var);
            }
            if(tmpSum!=sum.get(index)){
                return false;
            }
        }
        return true;
    }
}
