
import java.util.*;



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
    public int rows;
    public int cols;
    public int ALL_CELLS = -5; // Used in the findNeighborCells function
    public int OUT_OF_BOUNDS = -4;
    public int MARKED = -3;
    public int UNPROBED = -2;
    public int BOOM = -1;

    @Override    
    public void play(Map m) {
    	rows = m.rows();
    	cols = m.columns();
        // If map has not been probed yet, probe somewhere in the middle.
    	if(!m.probed()){
    		m.probe(Math.round(rows/2), Math.round(cols/2));
    	}

        int safeCounter = 0;
        boolean probedOrMarked;
        while(!m.done() && safeCounter<100){
            // m.display(); //maybe this is needed to make map show, dont know yet
            probedOrMarked = probeMap(m);
            if(!probedOrMarked){
                // This means we have no new information from probeMap
                // We have to make a guess here
            }
            safeCounter++;
        }
        
    	
   }

    public boolean probeMap(Map m){
    	
    	// NOTE: THIS IS TOTALLY UNTESTED ATM
        // Loop through map, find constraint cells and fringe cells
        // Returns true if a cell was probed or marked, false if nothing was done

        /* Variable descriptions
         - fringeCells and unassignedFringes:
        The indices of the fringe cells correspond to the indices of
        the unassigned fringe cells
         - constraints and constraintSums:
        Each array in constraints is a constraint. Each element in 
        each constraint array is an index corresponding to the index in the
        fringe cells array. 
        The index of each constraint corresponds to the index of each sum 
        in the constraintSums array.
        */


        int currentCell;
        ArrayList<Cell> unprobedNeighborCells;
        ArrayList<Cell> markedNeighborCells;
        ArrayList<Cell> fringeCells = new ArrayList<>();
        ArrayList<Integer> unassignedFringes = new ArrayList<>();
        ArrayList<Integer> constraint;
        ArrayList<ArrayList<Integer>> constraints = new ArrayList<>();
        ArrayList<Integer> constraintSums = new ArrayList<>();
        boolean newFringeCell;

        for(int row = 0; row<rows; row++){
            for(int col = 0; col<cols; col++){
                currentCell = m.look(row,col);
                
                // If cell has no mines around, probe all unprobed neighbor cells
                if(currentCell == 0){
                    unprobedNeighborCells = findNeighborCells(m, row, col, UNPROBED);
                    if(unprobedNeighborCells.size() != 0){
                        for(Cell cell:unprobedNeighborCells){
                            m.probe(cell.row, cell.col);
                        }
                        return true;
                    }
                }
                /* If the cell has neighbor mines, find all the neighbor fringe cells
                and save them. Also save the constraints*/
                else if(currentCell > 0){
                    unprobedNeighborCells = findNeighborCells(m, row, col, UNPROBED);
                    // Check each cell if already in fringe list, if not to list
                    for(Cell cell:unprobedNeighborCells){
                        newFringeCell = true;
                        for(Cell fringeCell:fringeCells){
                            if(fringeCell.equals(cell)){
                                newFringeCell = false;
                                break;
                            }
                        }
                        if(newFringeCell){
                            fringeCells.add(cell);
                            unassignedFringes.add(-1);
                        }
                    }
                    // Find and add constraints
                    if(unprobedNeighborCells.size() != 0){
                        markedNeighborCells = findNeighborCells(m, row, col, MARKED);
                        /* If number in cell is greater than the nr of marked neighbors we can
                        add a new constraint*/
                        if(currentCell > markedNeighborCells.size()){
                            constraint = new ArrayList<>();
                            for(Cell cell:unprobedNeighborCells){
                                for(Cell fringeCell:fringeCells){
                                    /* Finds the index of the current cell in the fringeCells
                                    This doesn't crash because of the loops above:
                                    fringeCells are scanned and found around each numbered cell.
                                    */
                                    if(cell.equals(fringeCell)){
                                        constraint.add(fringeCells.indexOf(fringeCell));
                                        // Index is same as in unassignedFringes
                                    }
                                }
                            }
                            // Add the constraint
                            constraints.add(constraint);
                            // The constraint sum will be the cell flag - nr of marked neighbors
                            constraintSums.add(currentCell - markedNeighborCells.size());
                        }
                    }
                }
            }
        }
        System.out.println(fringeCells.size());
        for(Cell test:fringeCells){
            String prntstr = test.row+" "+test.col;
            System.out.println(prntstr);
        }
        /* Now that we have all constraints and fringe cells, call the CSP solver and get
        all possible solutions back
        */
        ArrayList<ArrayList<Integer>> solutions = cspSolver(unassignedFringes, constraints, constraintSums);
        /*Now loop over the fringe cells and probe/flag all solved cells*/
        boolean probedOrMarked = false; // Return value of function
        if(solutions.size() != 0){
            boolean isMine;
            boolean isSafe;
            Cell fringeCell;
            for(int idx = 0; idx < fringeCells.size(); idx++){
                //Check if safe/mine in every solution
                isMine = true;
                isSafe = true;
                fringeCell = fringeCells.get(idx);
                for(ArrayList<Integer> solution:solutions){
                    if(solution.get(idx) != 1){
                        isMine = false;
                    }else if(solution.get(idx) != 0){
                        isSafe = false;
                    }
                    if(!isMine && !isSafe){
                        /* It's not a mine in every solution and it's not
                        safe in every solution, so we can break*/
                        break;
                    }
                }
                if(isMine){
                    m.mark(fringeCell.row, fringeCell.col);
                    probedOrMarked = true;
                }else if(isSafe){
                    m.probe(fringeCell.row, fringeCell.col);
                    probedOrMarked = true;
                }
            }
        }

        return probedOrMarked;

    }

    public ArrayList<Cell> findNeighborCells(Map m, int row, int col, int cellType){
        /* Returns an arraylist of all cells of type cellType neighboring the cell
        at location (row,col). if cellType == ALL_CELLS it returns all neighbors
        */
        int currentCell;
        ArrayList<Cell> returnList = new ArrayList<Cell>();;
        for(int rowFwd = -1; rowFwd<2; rowFwd++){
            for(int colFwd = -1; colFwd<2; colFwd++){
                // Skip own cell
                if(rowFwd == 0 && colFwd == 0){
                    continue;
                }
                currentCell = m.look(row+rowFwd, col+colFwd);
                if(cellType == ALL_CELLS || currentCell == cellType){
                    returnList.add(new Cell(row+rowFwd, col+colFwd, currentCell));
                }
            }
        }
        return returnList;
    }
    
    public ArrayList<ArrayList<Integer>> cspSolver(ArrayList<Integer> fringeAssignment, 
            ArrayList<ArrayList<Integer>> constraints, ArrayList<Integer> constraintSums){


        ArrayList<ArrayList<Integer>> solutions = new ArrayList<>();
        /*
        Step 1: Do DFS and stuff here
        Step 2: ???
        Step 3: profit!
        */

        return solutions;
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
        boolean partialTest;
        int unnasigned;
        for(int index=0;index<constraints.size();index++){
            tmpSum=0;
            partialTest=false;
            unnasigned=0;
            for(int var:constraints.get(index)){
                if(vars.get(var)==-1){
                    partialTest=true;
                    unnasigned++;
                }
                else{
                    tmpSum+=vars.get(var);
                }
            }
            if(partialTest==false && tmpSum!=sum.get(index)){
                return false;
            }
            else if(partialTest &&  tmpSum>sum.get(index)){
                return false;
            }
            else if( partialTest && tmpSum+unnasigned<sum.get(index)){
                return false;
            }
        }
        return true;
    }
}


class Cell{
    public int row; // row idx of the cell
    public int col; // col idx of the cell
    public int flag; // same notation as in Map (-3 ==> marked, -2 ==> unprobed etc)

    public Cell(int inputRow, int inputCol, int inputFlag){
        row = inputRow;
        col = inputCol;
        flag = inputFlag;
    }

    public boolean equals(Cell otherCell){
        // returns true if row and col are the same
        if(row == otherCell.row && col == otherCell.col){
            return true;
        }else{
            return false;
        }
    }
}