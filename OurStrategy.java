
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

    /*  POSSIBLE IMPROVEMENTS
        - Improve guessing so that you take into account how much
        new information you gain if you guess the correct cell, i.e.
        if the cell is not a mine, how many new solutions will I be
        able to find? Probably involves making a copy of the map
        and calling cspSolver again on a the copied map
        (probably more difficult)
        - Forward checking and all that other stuff in pdf
        (probably not very difficult, but possibly not very useful)
        - VERY USEFUL IMPROVEMENT: find fringes as clusters and solve
        the clusters individually. This way clusters that have many
        solutions but give no information will not impact performance
    */

    // SETTINGS BELOW, ENABLE ALL FOR GOOD PERFORMANCE, DISABLE FOR TESTING
    public boolean prioritizeCorners = true; // Prioritze corners when guessing
    public boolean goodGuessing = true; // Enables our guessing algorithms
    public boolean consistencyChecking = true; // Consistency checking in cspSolver
    public boolean ignoreLoneCells = true; // Ignore cells that give no info for constraints
    public boolean enablePrints = true; // Enable various prints in console


    public int rows;
    public int cols;
    public int ALL_CELLS = -5; // Used in the findNeighborCells function
    public int OUT_OF_BOUNDS = -4;
    public int MARKED = -3;
    public int UNPROBED = -2;

    @Override    
    public void play(Map m) {
        if(enablePrints){System.out.println("New game!");}
        rows = m.rows();
        cols = m.columns();
        // If map has not been probed yet, probe corner piece
        if(!m.probed()){
            m.probe(20,20);
        }
        long before = System.nanoTime();
        while(!m.done()){
            probeMap(m);
        }

        if(m.won()){
            if(enablePrints){System.out.println("Game won!");}
        }else{
            if(enablePrints){System.out.println("Game lost...");}
        }
        long after = System.nanoTime();
        String print= "It took "+(double)(after-before)/1000000000+" seconds to play the game";
        if(enablePrints){System.out.println(print);}
        
        
   }

    public void probeMap(Map m){
        
        // Loop through map, find constraint cells and fringe cells
        // If no consistent solutions, makes a guess.
        // This should maybe be split into a separate class with several different methods at this point..

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
         - allUnprobedCells:
        Array containing all unprobed cells. Used to make good guesses.
        */


        int currentCell;
        ArrayList<Cell> unprobedNeighborCells;
        ArrayList<Cell> markedNeighborCells;
        ArrayList<Cell> fringeCells = new ArrayList<>();
        ArrayList<Integer> unassignedFringes = new ArrayList<>();
        ArrayList<Integer> constraint;
        ArrayList<ArrayList<Integer>> constraints = new ArrayList<>();
        ArrayList<Integer> constraintSums = new ArrayList<>();
        ArrayList<Cell> allUnprobedCells = new ArrayList<Cell>();
        boolean newFringeCell;
        int nrMinesLeft = m.mines_minus_marks();
        boolean cellClearedNearby; // Used to check if a cleared cell can give useful info

        for(int x = 0; x<cols; x++){
            for(int y = 0; y<rows; y++){
                currentCell = m.look(x,y);

                // Save all unprobed cells
                if(currentCell == UNPROBED){
                    allUnprobedCells.add(new Cell(x,y,currentCell));
                }
                
                // If cell has no mines around, probe all unprobed neighbor cells
                if(currentCell == 0){
                    unprobedNeighborCells = findNeighborCells(m, x, y, UNPROBED);
                    if(unprobedNeighborCells.size() != 0){
                        for(Cell cell:unprobedNeighborCells){
                            m.probe(cell.x, cell.y);
                        }
                        return;
                    }
                }
                /* If the cell has neighbor mines, find all the neighbor fringe cells
                and save them. Also save the constraints*/
                else if(currentCell > 0){
                
                    unprobedNeighborCells = findNeighborCells(m, x, y, UNPROBED);
                    markedNeighborCells = findNeighborCells(m, x, y, MARKED);

                    // Check if this cell should be disregarded due to giving no information
                    if(ignoreLoneCells && currentCell < unprobedNeighborCells.size() + markedNeighborCells.size()){
                        cellClearedNearby = clearedCellsNearby(m, x, y, 2);
                        if(!cellClearedNearby){
                            // We gain no info from this cell, continue to next cell
                            // But remove nr of mines from the mine count
                            nrMinesLeft += -(currentCell - markedNeighborCells.size());
                            continue;
                        }
                    }

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
                        /* If number in cell is greater than the nr of marked neighbors we can
                        add a new constraint*/
                        if(currentCell >= markedNeighborCells.size()){
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

        if(fringeCells.size() == 0){
            /* No fringe! Can happen for instance if you click bottom
            and a 3 shows up and you mark all cells around you.*/
            Cell safestCell = getRandomCell(allUnprobedCells);
            m.probe(safestCell.x, safestCell.y);
            if(enablePrints){System.out.println("No fringe cells, probing random!");}
            return;
        }

        /* Now that we have all constraints and fringe cells, call the CSP solver and get
        all possible solutions back
        */
        ArrayList<ArrayList<Integer>> solutions = new ArrayList<ArrayList<Integer>>();
        cspSolver(unassignedFringes, constraints, constraintSums, solutions, 0, 0, nrMinesLeft);

        /*Now loop over the fringe cells and probe/flag all solved cells*/
        boolean probedOrMarked = false; // Return value of function
        // nrSafeCells counts nr of safe returns for each fringe cell, used to make guess
        int[] nrSafeCells = new int[fringeCells.size()]; 
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
                    if(solution.get(idx) != 1){ // Is safe
                        isMine = false;
                        nrSafeCells[idx] += 1;
                    }else if(solution.get(idx) != 0){ // Is mine
                        isSafe = false;
                    }
                }
                // Probe or mark the cell
                if(isMine){
                    m.mark(fringeCell.x, fringeCell.y);
                    probedOrMarked = true;
                }else if(isSafe){
                    m.probe(fringeCell.x, fringeCell.y);
                    probedOrMarked = true;
                }
            }
        }


        // Nothing was probed or marked, we need to make a guess...
        if(!probedOrMarked){
            if(!goodGuessing){
                // All guessing algorithms deactivated
                Cell guessedCell = getRandomCell(allUnprobedCells);
                m.probe(guessedCell.x, guessedCell.y);
            }
            if(solutions.size() != 0){
                int maxNr = 0;
                int maxIdx = 0;
                // Find safest fringe cell
                for(int idx = 0; idx<nrSafeCells.length; idx++){
                    if(nrSafeCells[idx] > maxNr){
                        maxNr = nrSafeCells[idx];
                        maxIdx = idx;
                    }
                }
                // Check if a random guess would be better
                double randomProb = 1.0-(double)nrMinesLeft/allUnprobedCells.size();
                double bestFringeProb = (double)maxNr/solutions.size();
                String printstr = "";
                Cell safestCell;
                if(randomProb > bestFringeProb){
                    // Make random guess outside fringe, prioritize corners
                    ArrayList<Cell> unprobedNonFringeCells = subtractCells(
                        allUnprobedCells, fringeCells);
                    if(unprobedNonFringeCells.size() == 0){
                        // This should in theory never happpen, but just in case
                        safestCell = getRandomCell(allUnprobedCells);
                    }else{
                        safestCell = getRandomCell(unprobedNonFringeCells);
                    }
                    printstr = "Guessing RANDOM on ("+safestCell.x+","+safestCell.y+
                    ") with confidence "+randomProb;
                }else{
                    safestCell = fringeCells.get(maxIdx);
                    printstr = "Guessing on ("+safestCell.x+","+safestCell.y+
                    ") with confidence "+bestFringeProb;
                }
                if(enablePrints){System.out.println(printstr);}
                m.probe(safestCell.x, safestCell.y);
                return;
            }

        }

    }

    public ArrayList<Cell> findNeighborCells(Map m, int x, int y, int cellType){
        /* Returns an arraylist of all cells of type cellType neighboring the cell
        at location (x,y). if cellType == ALL_CELLS it returns all neighbors
        */
        int currentCell;
        ArrayList<Cell> returnList = new ArrayList<Cell>();;
        for(int xFwd = -1; xFwd<2; xFwd++){
            for(int yFwd = -1; yFwd<2; yFwd++){
                // Skip own cell
                if(xFwd == 0 && yFwd == 0){
                    continue;
                }
                currentCell = m.look(x+xFwd, y+yFwd);
                if(cellType == ALL_CELLS || currentCell == cellType){
                    returnList.add(new Cell(x+xFwd, y+yFwd, currentCell));
                }
            }
        }
        return returnList;
    }

    public boolean clearedCellsNearby(Map m, int x, int y, int distance){
        int currentCell;
        for(int xFwd = -distance; xFwd<distance+1; xFwd++){
            for(int yFwd = -distance; yFwd<distance+1; yFwd++){
                // Skip own cell
                if(xFwd == 0 && yFwd == 0){
                    continue;
                }
                currentCell = m.look(x+xFwd,y+yFwd);
                if(currentCell >= 0){
                    return true;
                }
            }
        }
        return false;
    }
    

    public void cspSolver(ArrayList<Integer> fringeAssignment, 
            ArrayList<ArrayList<Integer>> constraints, ArrayList<Integer> constraintSums, 
            ArrayList<ArrayList<Integer>> solutions, int index, int assignedMines, 
            int nrMinesLeft){

        // Base case
        if(fringeAssignment.get(fringeAssignment.size()-1) != -1){
            if(constraintSatisfied(fringeAssignment, constraints, constraintSums, false)){
                solutions.add(fringeAssignment);
            }
            return;
        }

        ArrayList<Integer> nextAssignment;
        for(int i=0; i<2; i++){
            nextAssignment = new ArrayList<>(fringeAssignment);
            nextAssignment.set(index, i);

            if(constraintSatisfied(nextAssignment, 
                constraints, constraintSums, true) && assignedMines<=nrMinesLeft){
                // Only go deeper if current assignment does not break constraints
                cspSolver(nextAssignment, constraints, constraintSums, 
                    solutions, index+1, assignedMines+i, nrMinesLeft);
            }
        }

    }
    
    /**
 * Loops through the constraints and checks if they are satisfied: 
 * @param vars as ArrayList
 * @param constraints ArrayList
 * @param sum ArrayList
 * @return boolean
 */
    public boolean constraintSatisfied(ArrayList<Integer> vars, 
            ArrayList<ArrayList<Integer>> constraints, ArrayList<Integer> sum,
            boolean forwardChecking){

        int tmpSum;
        boolean partialTest;
        int unassigned;
        partialTest=false;
        int firstUnassigned=0;
        for(int index=0;index<constraints.size();index++){
            tmpSum=0;
            unassigned=0;
            for(int var:constraints.get(index)){
                if(vars.get(var)==-1){
                    partialTest=true;
                    unassigned++;
                    if(firstUnassigned==0){
                        firstUnassigned=var;
                    }
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
            else if( partialTest && tmpSum+unassigned<sum.get(index)){
                return false;
            }
        }
        if(partialTest && forwardChecking){
                for(int i=firstUnassigned;i<vars.size();i++){
                    ArrayList<Integer> l=domain(vars,constraints, sum, i, false);
                    if(l.isEmpty()){
                        return false;
                    }
                }
            }
        return true;
    }    
        public ArrayList<Integer> domain(ArrayList<Integer> assigned, 
            ArrayList<ArrayList<Integer>> constraints, ArrayList<Integer> sum, 
            int index, boolean forward){
        ArrayList<Integer> domainList = new ArrayList();
        ArrayList<Integer> nextAssignment=  new ArrayList<>(assigned);
        for(int i=0; i<2;i++){
            nextAssignment.set(index, i);
            if(constraintSatisfied(nextAssignment,constraints, sum, forward)){
                domainList.add(i);
            }
        } 
        return domainList;
        
    }


    public ArrayList<Cell> subtractCells(ArrayList<Cell> originalList, ArrayList<Cell> subtractList){
        ArrayList<Cell> returnList = new ArrayList<Cell>(originalList);
        for(Cell removeCell:subtractList){
            for(Cell origCell:originalList){
                if(origCell.equals(removeCell)){
                    returnList.remove(origCell);
                    break;
                }
            }
        }
        return returnList;
    }

    public Cell getRandomCell(ArrayList<Cell> cellList){

        if(prioritizeCorners){
            // Try corners first
            int[][] cornerCoords = {{0,0},{0,rows-1},{cols-1,0},{cols-1,rows-1}};
            for(int[] coords:cornerCoords){
                for(Cell currentCell:cellList){
                    if(currentCell.x == coords[0] && currentCell.y == coords[1]){
                        return currentCell;
                    }
                }
            }
        }

        // No corner cell is unprobed, return completely random cell
        return cellList.get(new Random().nextInt(cellList.size()));

    }

}



class Cell{
    public int x; // x idx of the cell
    public int y; // y idx of the cell
    public int flag; // same notation as in Map (-3 ==> marked, -2 ==> unprobed etc)

    public Cell(int inputX, int inputy, int inputFlag){
        x = inputX;
        y = inputy;
        flag = inputFlag;
    }

    public boolean equals(Cell otherCell){
        // returns true if x and y are the same
        if(x == otherCell.x && y == otherCell.y){
            return true;
        }else{
            return false;
        }
    }
}