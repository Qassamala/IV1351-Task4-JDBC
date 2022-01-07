/*
 * The MIT License (MIT)
 * Copyright (c) 2020 Leif Lindbäck
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction,including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so,subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package se.kth.iv1351.task4;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Scanner;

/**
 * A small program that illustrates how to write a simple JDBC program.
 */
public class Task4JDBC {
  private static final String TABLE_NAME = "inventory_instrument";
  private static final String TABLE_RENTAL = "instrument_rental";
  private static final String TABLE_STUDENT = "student";


  private static PreparedStatement createInstrumentalRentalStmt;
  private static PreparedStatement findAllAvailableInstrumentsStmt;
  private static PreparedStatement terminateRentalStmt;
  private static PreparedStatement findAllRentalsStmt;
  private static PreparedStatement findStudentStmt;
  private static PreparedStatement updateAvailableInstrumentStmt;


  private  static Connection createConnection() throws SQLException, ClassNotFoundException {
    Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/soundgood_db",
      "postgres", "postgres");
      connection.setAutoCommit(false);
      return connection;
  }


  private static void listAllAvailableInstruments(Connection connection) {
    ResultSet instrumentsAvailable = null;
    try {
      instrumentsAvailable = findAllAvailableInstrumentsStmt.executeQuery();
      while (instrumentsAvailable.next()) {
        System.out.println(
            "id: " + instrumentsAvailable.getInt(1) + ", type: " + instrumentsAvailable.getString(2) + ", brand: " + instrumentsAvailable.getString(3) + ", available: " + instrumentsAvailable.getBoolean(4) + ", fee: " + instrumentsAvailable.getDouble(5));
      }
      connection.commit();
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);
    } finally{
      closeResultSet(instrumentsAvailable);
    }
  }

  private static void listAllInstrumentRentals(Connection connection) {
    ResultSet rentals = null;
    try  {
      rentals = findAllRentalsStmt.executeQuery();
      while (rentals.next()) {
        System.out.println(
            "student_id: " + rentals.getInt(1) + ", inventory_instrument_id: " + rentals.getInt(2) + ", startDate: " + rentals.getDate(3) + ", endDate: "  + rentals.getDate(4)  + ", returnedDate: " + rentals.getDate(5) + ", fee: " + rentals.getDouble(6) + ", returned: " + rentals.getBoolean(7));
      }
      connection.commit();
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);
    } finally{
      closeResultSet(rentals);
    }
  }

  private static void createRental(Connection connection, int studentId, int instrumentId) {
    // Check if student ID exists
    ResultSet students = null;
    boolean studentFound = false;
    try  {
      students = findStudentStmt.executeQuery();
      while (students.next()) {
        if(studentId == students.getInt(1))
          studentFound = true;
      }
      connection.commit();
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);
    } finally{
      closeResultSet(students);
    }

    if(!studentFound){
      System.out.println("StudentId not found");
    } else {

    // check nr of rentals for student ID
    ResultSet rentals = null;
    int nrOfRentals = 0;
    try  {
      rentals = findAllRentalsStmt.executeQuery();
      while (rentals.next()) {
        if(studentId == rentals.getInt(1) && rentals.getBoolean(7) == false)
          nrOfRentals++;
      }
      connection.commit();
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);
    } finally{
      closeResultSet(rentals);
    }

    
    if(nrOfRentals > 1)
      System.out.println("The user is already renting the maximum nr of 2 rentals");
    else {
      boolean instrumentAvailable = false;
      double instrumentFee = 0.0;
      ResultSet instrumentsAvailable = null;

    // Part of ACID transaction below:  
    try {
      instrumentsAvailable = findAllAvailableInstrumentsStmt.executeQuery();
      
      while (instrumentsAvailable.next()) {
        if(instrumentsAvailable.getInt(1) == instrumentId && instrumentsAvailable.getBoolean(4) == true)  // check if instrument exists as available for renting
          {
            instrumentAvailable = true;
            instrumentFee = instrumentsAvailable.getDouble(5);  // retrives the instrument fee to be applied on the rental contract
          }
      }
      LocalDateTime endDate = java.sql.Timestamp.from(java.time.Instant.now()).toLocalDateTime().plusYears(1);  // set endDate one year ahead
      
      if(instrumentAvailable){
        createInstrumentalRentalStmt.setInt(1, studentId);  // composite key
        createInstrumentalRentalStmt.setInt(2, instrumentId); // composite key
        createInstrumentalRentalStmt.setTimestamp(3, java.sql.Timestamp.from(java.time.Instant.now())); // composite key
        createInstrumentalRentalStmt.setTimestamp(4, java.sql.Timestamp.valueOf(endDate));  // endDate
        createInstrumentalRentalStmt.setTimestamp(5, null); // returnedDate
        createInstrumentalRentalStmt.setDouble(6, instrumentFee); // fee
        createInstrumentalRentalStmt.setBoolean(7, false);  // returned
        createInstrumentalRentalStmt.executeUpdate();

        updateAvailableInstrumentStmt.setBoolean(1, false);
        updateAvailableInstrumentStmt.setInt(2, instrumentId);
        updateAvailableInstrumentStmt.executeUpdate();

        connection.commit();
        System.out.println("Rented instrument ID: " + instrumentId );

      } else {
        System.out.println("Instrument with id: " + instrumentId + " is either not found or available.");
      }
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);  //this part rolls back the transaction
    } finally{
      closeResultSet(instrumentsAvailable);
    }

    }
  }

  }

  private static void terminateRental(Connection connection, int studentId, int instrumentId, Timestamp startDate) {
    LocalDate date = startDate.toLocalDateTime().toLocalDate();
    ResultSet rentals = null;
    try  {
      rentals = findAllRentalsStmt.executeQuery();
      while (rentals.next()) {
        if(studentId == rentals.getInt(1) && instrumentId == rentals.getInt(2) && date.equals(rentals.getTimestamp(3).toLocalDateTime().toLocalDate()) && rentals.getBoolean(7) == false){
          terminateRentalStmt.setBoolean(1, true); // returned
          terminateRentalStmt.setTimestamp(2, java.sql.Timestamp.from(java.time.Instant.now()));  // returnedDate
          terminateRentalStmt.setInt(3, studentId); // Composite key
          terminateRentalStmt.setInt(4, instrumentId);  // Composite key
          terminateRentalStmt.setTimestamp(5, rentals.getTimestamp(3)); // Composite key
          terminateRentalStmt.executeUpdate();

          updateAvailableInstrumentStmt.setBoolean(1, true);
          updateAvailableInstrumentStmt.setInt(2, instrumentId);
          updateAvailableInstrumentStmt.executeUpdate();
          System.out.println("Rental terminated");
        }

      }
      connection.commit();
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);  // this rolls back the transaction.
    } finally{
      closeResultSet(rentals);
    }
    
  }

  private static void listAllStudents(Connection connection) {
    
    ResultSet students = null;
    try  {
      students = findStudentStmt.executeQuery();
      System.out.println("*** List of students ***");
      while (students.next()) {
        System.out.println(
            "id: " + students.getInt(1) + ", personnumber: " + students.getString(2) + ", firstName: " + students.getString(3) + ", lastName: "  + students.getString(4));
      }
      connection.commit();
    } catch (SQLException sqle) {
      sqle.printStackTrace();
      handleException(connection);
    } finally{
      closeResultSet(students);
    }
  }

  private static void prepareStatements(Connection connection) throws SQLException {
    createInstrumentalRentalStmt = connection.prepareStatement("INSERT INTO " + TABLE_RENTAL + " VALUES (?, ?, ?, ?, ?, ?, ?)");
    findAllAvailableInstrumentsStmt = connection.prepareStatement("SELECT * from " + TABLE_NAME + " WHERE available = true");
    terminateRentalStmt = connection.prepareStatement("UPDATE " + TABLE_RENTAL + " SET returned =?, returnedDate =?  WHERE student_id = ? AND inventory_instrument_id =? AND startDate=?");
    updateAvailableInstrumentStmt = connection.prepareStatement("UPDATE " + TABLE_NAME + " SET available =?  WHERE id = ?");
    findAllRentalsStmt = connection.prepareStatement("SELECT * from " + TABLE_RENTAL);
    findStudentStmt = connection.prepareStatement("SELECT * from " + TABLE_STUDENT);
    
  }

  public static void main(String[] args) {

    try (Connection connection = createConnection()) {
      prepareStatements(connection);
       
      Scanner in = new Scanner(System.in);
      int selection;
      
      boolean run = true;
      System.out.println("\nPress 1 to list available instruments\nPress 2 to list all rentals\nPress 3 to rent instrument\nPress 4 to terminate specific rental\nPress 5 to list commands\nPress 6 exit\n");
      
      while (run) {
        System.out.println("Select an option or press 5 to list available commands");
        selection = in.nextInt();
        switch (selection) {
          case 1:
            System.out.println("*** Available instruments ***");
            listAllAvailableInstruments(connection);
            break;
          case 2:
            System.out.println("*** All rentals ***");
            listAllInstrumentRentals(connection);
            break;
          case 3:
          System.out.println("*** Rent instrument menu ***");
            listAllStudents(connection);
            System.out.println("Enter student ID here");
            int studentId = in.nextInt();
            System.out.println("Enter instrument ID here");
            int instrumentId = in.nextInt();
            createRental(connection, studentId, instrumentId);
            break;
          case 4:
          System.out.println("*** Terminate instrument menu here ***");
            listAllInstrumentRentals(connection);
            System.out.println("Enter student ID here");
            studentId = in.nextInt();
            System.out.println("Enter instrument ID here");
            instrumentId = in.nextInt();
            System.out.println("Enter startDate here as YYYY-MM-DD");
            String startDateString = in.next();
            startDateString = startDateString.concat(" 00:00:00");
            java.sql.Timestamp startDate = java.sql.Timestamp.valueOf( startDateString ) ;  // convert string to timestamp here
            terminateRental(connection, studentId, instrumentId, startDate);
            break;
          case 5:
            System.out.println("\nPress 1 to list available instruments\nPress 2 to list all rentals\nPress 3 to rent instrument\nPress 4 to terminate specific rental\nPress 5 to list commands\nPress 6 exit\n");
            break;
          case 6:
            System.out.println("Exited program");
            run = false;
            break;
          default:
          System.out.println("Default reached");
            break;
        }
        System.out.println();
      }
      connection.close(); 
      
    } catch (SQLException | ClassNotFoundException exc) {
      exc.printStackTrace();
    }
    
  }

  private static void closeResultSet(ResultSet resultSet){
    try {
      resultSet.close();  // must be wrapped in try catch clause
    } catch (SQLException e) {
      System.out.println("could not close connection due to: " + e);
    }
    
  }

  private static void handleException(Connection connection) {
    try {
        connection.rollback();
    } catch (SQLException e) {
       System.out.println("could not roll back transaction due to: " + e );
    }
}


}
