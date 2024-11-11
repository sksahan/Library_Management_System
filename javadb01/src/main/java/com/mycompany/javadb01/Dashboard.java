/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.mycompany.javadb01;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.*;

import java.awt.event.ActionEvent;

import java.awt.event.ActionListener;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 *
 * @author sahan
 */
public class Dashboard extends javax.swing.JFrame {
      

    /**
     * Creates new form Dashboard
     */
    public Dashboard() {
        initComponents();
        loadCategories(); 
        
    loadTotals();
        
    AddBook.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
    addBook();
    }
});
    
    
    }
    
    ///////////////////////////////// count //////////////////////////////////////////////////
  
private void loadTotals() {
    Connection conn = null; // Declare conn outside the try block
    try {
        conn = DatabaseHelper.connect(); // Initialize conn
        conn.setAutoCommit(false); // Set auto-commit to false

        // Query to get total available books
        String totalAvailableBooksQuery = "SELECT SUM(Quantity) AS total FROM Books WHERE Status = 'Available'";
        int totalAvailableBooks = 0; // Initialize available books count

        try (PreparedStatement stmt = conn.prepareStatement(totalAvailableBooksQuery);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                totalAvailableBooks = rs.getInt("total");
                TotalavailableBook.setText(String.valueOf(totalAvailableBooks)); // Set only the number
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error retrieving available books count: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Total Members
        String totalMembersQuery = "SELECT COUNT(*) AS total FROM Members";
        try (PreparedStatement stmt = conn.prepareStatement(totalMembersQuery);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int totalMembers = rs.getInt("total");
                TotalMembers.setText(String.valueOf(totalMembers));
            }
        }

        // Count Total Borrowed Books (where Return_Date is NULL)
        String totalBorrowedBooksQuery = "SELECT COUNT(*) AS TotalBorrowedBooks FROM Transactions WHERE Return_Date IS NULL";
        int totalBorrowedBooks = 0; // Initialize borrowed books count

        try (PreparedStatement stmt = conn.prepareStatement(totalBorrowedBooksQuery);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                totalBorrowedBooks = rs.getInt("TotalBorrowedBooks");
                TotalBarrow.setText(String.valueOf(totalBorrowedBooks)); // Update the correct label
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Error retrieving borrowed books count: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        // Calculate total books
        int totalBooks = totalAvailableBooks + totalBorrowedBooks;

        // Update the JLabel with the total number of books
        TotalBooks.setText(String.valueOf(totalBooks)); // Update the correct label

        // Commit the transaction
        conn.commit();
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Error loading totals: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        // Handle rollback in case of an error
        try {
            if (conn != null) {
                conn.rollback();
            }
        } catch (SQLException rollbackEx) {
            JOptionPane.showMessageDialog(this, "Error during rollback: " + rollbackEx.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    } finally {
        try {
            if (conn != null) {
                conn.setAutoCommit(true); // Reset auto-commit to true
                conn.close(); // Close the connection
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error resetting auto-commit: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
    
    ///////////////////////////////////return book /////////////////////////////////////////////
    
private void returnBook() {

    String memberIdStr = ReturnMemberID.getText().trim();

    String isbn = ReturnBookISBN.getText().trim();


    // Validate input

    if (memberIdStr.isEmpty() || isbn.isEmpty()) {

        JOptionPane.showMessageDialog(this, "Please enter both Member ID and ISBN.", "Input Error", JOptionPane.ERROR_MESSAGE);

        return;

    }


    int memberId;

    try {

        memberId = Integer.parseInt(memberIdStr);

    } catch (NumberFormatException e) {

        JOptionPane.showMessageDialog(this, "Member ID must be a number.", "Input Error", JOptionPane.ERROR_MESSAGE);

        return;

    }


    try (Connection conn = DatabaseHelper.connect()) {

        if (conn == null) {

            JOptionPane.showMessageDialog(this, "Failed to connect to the database.", "Connection Error", JOptionPane.ERROR_MESSAGE);

            return;

        }


        conn.setAutoCommit(false);  // Begin transaction


        // Check if the transaction exists

        String transactionQuery = "SELECT * FROM Transactions WHERE Member_ID = ? AND Book_ID = (SELECT Book_ID FROM Books WHERE ISBN = ?)";

        try (PreparedStatement transactionStmt = conn.prepareStatement(transactionQuery)) {

            transactionStmt.setInt(1, memberId);

            transactionStmt.setString(2, isbn);

            ResultSet transactionRs = transactionStmt.executeQuery();


            if (!transactionRs.next()) {

                JOptionPane.showMessageDialog(this, "No transaction found for this Member ID and ISBN.", "Error", JOptionPane.ERROR_MESSAGE);

                conn.rollback();

                return;

            }


            // Retrieve the issue date

            Date issueDate = transactionRs.getDate("Issue_Date");

            Date currentDate = new Date(System.currentTimeMillis());


            // Calculate the due date (assuming a 14-day loan period)

            long dueDateMillis = issueDate.getTime() + (14 * 24 * 60 * 60 * 1000); // Add 14 days in milliseconds

            Date dueDate = new Date(dueDateMillis); // Convert back to java.sql.Date


            // Calculate fine if the book is returned late

            long diffInMillies = currentDate.getTime() - dueDate.getTime();

            long daysLate = diffInMillies / (1000 * 60 * 60 * 24); // Convert milliseconds to days

            double fine = (daysLate > 0) ? daysLate * 10 : 0; // RS 10 per day


            // Update the book's quantity and status

            String bookQuery = "UPDATE Books SET Quantity = Quantity + 1, Status = CASE WHEN Quantity + 1 = 1 THEN 'Available' ELSE Status END WHERE ISBN = ?";

            try (PreparedStatement bookStmt = conn.prepareStatement(bookQuery)) {

                bookStmt.setString(1, isbn);

                bookStmt.executeUpdate();

            }


            // Update the return date and fine in the Transactions table

            String updateTransactionQuery = "UPDATE Transactions SET Return_Date = ?, Fine = ? WHERE Member_ID = ? AND Book_ID = (SELECT Book_ID FROM Books WHERE ISBN = ?)";

            try (PreparedStatement updateTransactionStmt = conn.prepareStatement(updateTransactionQuery)) {

                java.sql.Date returnDate = new java.sql.Date(System.currentTimeMillis()); // Correctly create java.sql.Date

                updateTransactionStmt.setDate(1, returnDate);

                updateTransactionStmt.setDouble(2, fine);

                updateTransactionStmt.setInt(3, memberId);

                updateTransactionStmt.setString(4, isbn);

                updateTransactionStmt.executeUpdate();

            }


                // Commit transaction

                conn.commit();


                // Show success message with fine information

                String fineMessage = (fine > 0) ? " You have a fine of RS " + fine + " for late return." : "";

                JOptionPane.showMessageDialog(this, "Book returned successfully!" + fineMessage, "Success", JOptionPane.INFORMATION_MESSAGE);


                // Clear input fields

                ReturnMemberID.setText("");

                ReturnBookISBN.setText("");


            } catch (SQLException e) {

                // Rollback transaction in case of error

                conn.rollback();

                JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);

            }

        } catch (SQLException e) {

            JOptionPane.showMessageDialog(this, "Database connection error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);

        }

    }
    ///////////////////////barrow book ///////////////////////////////////////////////////////////////////
    
 private void borrowBook() throws SQLException {
    String memberIdStr = BrrowMemberID.getText().trim();
    String isbn = BarrowBookISBN.getText().trim();

    // Validate input
    if (memberIdStr.isEmpty() || isbn.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please enter both Member ID and ISBN.", "Input Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    int memberId;
    try {
        memberId = Integer.parseInt(memberIdStr);
    } catch (NumberFormatException e) {
        JOptionPane.showMessageDialog(this, "Member ID must be a number.", "Input Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Use a single connection for the whole process
    try (Connection conn = DatabaseHelper.connect()) {
        conn.setAutoCommit(false);  // Begin transaction

        // Check if Member_ID exists
        String memberQuery = "SELECT * FROM Members WHERE Member_ID = ?";
        try (PreparedStatement memberStmt = conn.prepareStatement(memberQuery)) {
            memberStmt.setInt(1, memberId);
            ResultSet memberRs = memberStmt.executeQuery();

            if (!memberRs.next()) {
                JOptionPane.showMessageDialog(this, "Wrong Member ID", "Error", JOptionPane.ERROR_MESSAGE);
                conn.rollback();
                return;
            }
        }

        // Check if the book is available
        String bookQuery = "SELECT * FROM Books WHERE ISBN = ?";
        int bookId;
        int quantity;
        String status;
        try (PreparedStatement bookStmt = conn.prepareStatement(bookQuery)) {
            bookStmt.setString(1, isbn);
            ResultSet bookRs = bookStmt.executeQuery();

            if (!bookRs.next()) {
                JOptionPane.showMessageDialog(this, "Book not found", "Error", JOptionPane.ERROR_MESSAGE);
                conn.rollback();
                return;
            }

            bookId = bookRs.getInt("Book_ID");
            quantity = bookRs.getInt("Quantity");
            status = bookRs.getString("Status");

            if (quantity <= 0 || status.equals("Issued")) {
                JOptionPane.showMessageDialog(this, "Book is currently not available", "Error", JOptionPane.ERROR_MESSAGE);
                conn.rollback();
                return;
            }
        }

        // Proceed to borrow the book
        String transactionQuery = "INSERT INTO Transactions (Book_ID, Member_ID, Issue_Date) VALUES (?, ?, ?)";
        try (PreparedStatement transactionStmt = conn.prepareStatement(transactionQuery)) {
            Date issueDate = new Date(System.currentTimeMillis());
            Date returnDate = new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000); // 1 week later
         //   double fine = 0.00; // Assuming no fine at the time of borrowing

            transactionStmt.setInt(1, bookId);
            transactionStmt.setInt(2, memberId);
            transactionStmt.setDate(3, issueDate);
         //   transactionStmt.setDate(4, returnDate);
         //   transactionStmt.setDouble(4, fine);
            transactionStmt.executeUpdate();

            // Update return date label
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            BookReturnDate.setText(sdf.format(returnDate));
        }

        // Update the quantity and status of the book
        String updateBookQuery = "UPDATE Books SET Quantity = ?, Status = ? WHERE Book_ID = ?";
        try (PreparedStatement updateBookStmt = conn.prepareStatement(updateBookQuery)) {
            int newQuantity = quantity - 1;
            String newStatus = (newQuantity == 0) ? "Not Available" : "Available";

            updateBookStmt.setInt(1, newQuantity);
            updateBookStmt.setString(2, newStatus);
            updateBookStmt.setInt(3, bookId);
            updateBookStmt.executeUpdate();
        }

        // Commit transaction
        conn.commit();

        // Show success message
        JOptionPane.showMessageDialog(this, "Book borrowed successfully! Please return it by " + BookReturnDate.getText(), "Success", JOptionPane.INFORMATION_MESSAGE);

        BrrowMemberID.setText("");
        BarrowBookISBN.setText("");
        BookReturnDate.setText("YYYY/MM/DD");

        BrrowMemberID.requestFocus();
        
    } catch (SQLException e) {
        // Rollback transaction in case of error
        try (Connection conn = DatabaseHelper.connect()) {
            conn.rollback();
        } catch (SQLException rollbackEx) {
            JOptionPane.showMessageDialog(this, "Failed to rollback transaction: " + rollbackEx.getMessage(), "Rollback Error", JOptionPane.ERROR_MESSAGE);
        }
        JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////
        
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////
    
    private void loadCategories() {

    String query = "SELECT Category_Name FROM Categories";
    try (Connection conn = DatabaseHelper.connect();
         PreparedStatement stmt = conn.prepareStatement(query);
         ResultSet rs = stmt.executeQuery()) {

        // Clear existing items
        Categori.removeAllItems();

        // Add a default item
        Categori.addItem("Select a category");

        // Populate JComboBox with categories from the database
        while (rs.next()) {
            String categoryName = rs.getString("Category_Name");
            Categori.addItem(categoryName);
        }
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Error loading categories: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
    }
}
    
    /////////////////////////////////////set table alignment center ///////////////////
    
public class CenterRenderer extends DefaultTableCellRenderer {

    @Override

    public Component getTableCellRendererComponent(JTable table, Object value,
                   boolean isSelected, boolean hasFocus, int row, int column) {
        Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        setHorizontalAlignment(SwingConstants.CENTER);

        return cell;

    }

}

//////////////////////////set table alignment left //////////////////////////////

public class LeftRenderer extends DefaultTableCellRenderer {

    @Override

    public Component getTableCellRendererComponent(JTable table, Object value,

                                                   boolean isSelected, boolean hasFocus, int row, int column) {        
        Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setHorizontalAlignment(SwingConstants.LEFT);

        return cell;

    }

}


    
/////////////////////////////////////////Available Books ////////////////////////////
  private void loadAvailableBooks() {
    String query = "SELECT b.Book_ID, b.Title, c.Category_Name, b.Quantity " +
                   "FROM Books b " +
                   "JOIN Categories c ON b.Category_ID = c.Category_ID " +
                   "WHERE b.Status = 'Available'";

    try (Connection conn = DatabaseHelper.connect();
         PreparedStatement stmt = conn.prepareStatement(query);
         ResultSet rs = stmt.executeQuery()) {

        DefaultTableModel model = (DefaultTableModel) BookTable.getModel();
        model.setRowCount(0); // Clear existing rows

        while (rs.next()) {
            int bookId = rs.getInt("Book_ID");
            String title = rs.getString("Title");
            String categoryName = rs.getString("Category_Name");
            int quantity = rs.getInt("Quantity");

            model.addRow(new Object[]{bookId, title, categoryName, quantity});
        }
        
        BookTable.getColumnModel().getColumn(0).setCellRenderer(new CenterRenderer());
        BookTable.getColumnModel().getColumn(1).setCellRenderer(new LeftRenderer());
        BookTable.getColumnModel().getColumn(2).setCellRenderer(new CenterRenderer());
        BookTable.getColumnModel().getColumn(3).setCellRenderer(new CenterRenderer());
        
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Error loading available books: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
    }
}
  
//////////////////////////////////////////logout/////////////////////////////////////
    
    private void logoutActionPerformed(java.awt.event.ActionEvent evt) {
    // Optionally, you can clear any session data here

    // Close the current dashboard window
    this.dispose(); // This will close the current JFrame

    // Open the login window
    login loginFrame = new login(); // Assuming you have a LoginFrame class
    loginFrame.setVisible(true); // Show the login frame
}
///////////////////////////////////////////add Admin//////////////////////////////////
    
 private void addAdmin() {
    String username = AdminUserName.getText().trim();
    String password = AdminPass.getText().trim(); // Using AdminPass for password
    String email = Admin_Email.getText().trim(); // Using Admin_Email for email

    // Validate input fields
    if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please fill in all fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Hash the password (you can use a library like BCrypt for this)
    String hashedPassword = hashPassword(password); // Implement this method to hash the password

    // SQL Query to insert admin
    String query = "INSERT INTO Admin (Username, Password, Email) VALUES (?, ?, ?)";

    try (Connection conn = DatabaseHelper.connect();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, username);
        stmt.setString(2, hashedPassword);
        stmt.setString(3, email);
        stmt.executeUpdate();

        JOptionPane.showMessageDialog(this, "Admin added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        clearAdminFields(); // Call a method to clear the form fields
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}

// Method to clear the admin form fields
private void clearAdminFields() {
    AdminUserName.setText("");
    AdminPass.setText(""); // Clear password field
    Admin_Email.setText(""); // Clear email field
}

// Example method to hash the password (you can use a library like BCrypt)
private String hashPassword(String password) {
    // Implement your password hashing logic here
    return password; // Replace this with the actual hashed password
}
/////////////////////////////////////////Add Member////////////////////////////////////
    
    private void addMember() {
    String firstName = MemberFirstName.getText().trim();
    String lastName = MemberLastName.getText().trim();
    String address = MemberAdress.getText().trim();
    String phone = MemberPhone.getText().trim();
    String email = MemberEmail.getText().trim();

    if (firstName.isEmpty() || lastName.isEmpty() || address.isEmpty() || phone.isEmpty() || email.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please fill in all fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // SQL Query to insert member
    String query = "INSERT INTO Members (First_Name, Last_Name, Address, Phone, Email) VALUES (?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseHelper.connect();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, firstName);
        stmt.setString(2, lastName);
        stmt.setString(3, address);
        stmt.setString(4, phone);
        stmt.setString(5, email);
        stmt.executeUpdate();

        JOptionPane.showMessageDialog(this, "Member added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        clearMemberFields(); // Call a method to clear the form fields
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}

// Method to clear the form fields
private void clearMemberFields() {
    MemberFirstName.setText("");
    MemberLastName.setText("");
    MemberAdress.setText("");
    MemberPhone.setText("");
    MemberEmail.setText("");
}

/////////////////////////////////////////add Book///////////////////////////////////////    
    
private void addBook() {
    String title = Title.getText().trim();
    String firstName = FirstName.getText().trim();
    String secondName = SecondName.getText().trim();
    String category = (String) Categori.getSelectedItem();
    String publisher = Publisher.getText().trim();
    String publishYear = PublishYear.getText().trim();
    String isbn = Isbn.getText().trim();
    String quantityStr = quantity.getText().trim();

    if (title.isEmpty() || firstName.isEmpty() || secondName.isEmpty() || category == null || publisher.isEmpty() || publishYear.isEmpty() || isbn.isEmpty() || quantityStr.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please fill in all fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    int quantity;
    try {
        quantity = Integer.parseInt(quantityStr);
    } catch (NumberFormatException e) {
        JOptionPane.showMessageDialog(this, "Quantity must be a number.", "Input Error", JOptionPane.ERROR_MESSAGE);
        return;
    }
    
    // SQL Queries
    String authorQuery = "INSERT INTO Authors (First_Name, Last_Name) VALUES (?, ?)";
    String bookQuery = "INSERT INTO Books (Title, Author_ID, Category_ID, Publisher, Year_Published, ISBN, Quantity) VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseHelper.connect();
         PreparedStatement authorStmt = conn.prepareStatement(authorQuery, PreparedStatement.RETURN_GENERATED_KEYS);
         PreparedStatement bookStmt = conn.prepareStatement(bookQuery)) {
        
        Integer authorId = getAuthorId(firstName, secondName, conn);
        
        // Insert author
        
        if (authorId == null) {
            // Author does not exist, insert new author
            authorStmt.setString(1, firstName);
            authorStmt.setString(2, secondName);
            authorStmt.executeUpdate();
            // Get the generated Author ID
            authorId = getAuthorId(firstName, secondName, conn); // Retrieve the new ID

        }

        // Get category ID
        int categoryId = getCategoryId(category, conn);
         
        if (categoryId == -1) {
            JOptionPane.showMessageDialog(this, "Category not found.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Insert book
        bookStmt.setString(1, title);
        bookStmt.setInt(2, authorId);
        bookStmt.setInt(3, categoryId);
        bookStmt.setString(4, publisher);
        bookStmt.setString(5, publishYear);
        bookStmt.setString(6, isbn);
        bookStmt.setInt(7, quantity);
        bookStmt.executeUpdate();

        JOptionPane.showMessageDialog(this, "Book added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        clearFields(); 
        
    } catch (SQLException e) {
       JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}

private int getCategoryId(String category, Connection conn) throws SQLException {
    String query = "SELECT Category_ID FROM Categories WHERE Category_Name = ?";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, category);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt("Category_ID");
        }
    }
    return -1; // Return -1 if not found
}

private Integer getAuthorId(String firstName, String lastName, Connection conn) throws SQLException {
    String query = "SELECT Author_ID FROM Authors WHERE First_Name = ? AND Last_Name = ?";
    try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setString(1, firstName);
        stmt.setString(2, lastName);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return rs.getInt("Author_ID"); // Return the existing author's ID

        }
    }
    return null; // Return null if the author does not exist

}

private void clearFields() {
    Title.setText("");
    FirstName.setText("");
    SecondName.setText("");
    Publisher.setText("");
    PublishYear.setText("");
    Isbn.setText("");
    Categori.setSelectedIndex(0);
    quantity.setText("");
}

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jButton6 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        jButton9 = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        Home = new javax.swing.JPanel();
        jLabel28 = new javax.swing.JLabel();
        jLabel29 = new javax.swing.JLabel();
        jLabel31 = new javax.swing.JLabel();
        TotalBooks = new javax.swing.JLabel();
        TotalBarrow = new javax.swing.JLabel();
        TotalMembers = new javax.swing.JLabel();
        jLabel30 = new javax.swing.JLabel();
        TotalavailableBook = new javax.swing.JLabel();
        addBook = new javax.swing.JPanel();
        Title = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        SecondName = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        FirstName = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        Categori = new javax.swing.JComboBox<>();
        jLabel13 = new javax.swing.JLabel();
        Publisher = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        PublishYear = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        Isbn = new javax.swing.JTextField();
        jLabel16 = new javax.swing.JLabel();
        quantity = new javax.swing.JTextField();
        AddBook = new javax.swing.JButton();
        addMember = new javax.swing.JPanel();
        jLabel17 = new javax.swing.JLabel();
        MemberFirstName = new javax.swing.JTextField();
        jLabel18 = new javax.swing.JLabel();
        MemberLastName = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        MemberAdress = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        MemberPhone = new javax.swing.JTextField();
        jLabel21 = new javax.swing.JLabel();
        MemberEmail = new javax.swing.JTextField();
        AddMember = new javax.swing.JButton();
        addAdmin = new javax.swing.JPanel();
        jLabel22 = new javax.swing.JLabel();
        AdminUserName = new javax.swing.JTextField();
        AdminPassword = new javax.swing.JLabel();
        AdminPass = new javax.swing.JTextField();
        AdminEmail = new javax.swing.JLabel();
        Admin_Email = new javax.swing.JTextField();
        RegAdmin = new javax.swing.JButton();
        availableBooks = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        BookTable = new javax.swing.JTable();
        barrowBook = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        BrrowMemberID = new javax.swing.JTextField();
        jLabel24 = new javax.swing.JLabel();
        BarrowBookISBN = new javax.swing.JTextField();
        BookReturnDate = new javax.swing.JLabel();
        jLabel26 = new javax.swing.JLabel();
        ReleaseBook = new javax.swing.JButton();
        returnBook = new javax.swing.JPanel();
        jLabel25 = new javax.swing.JLabel();
        ReturnMemberID = new javax.swing.JTextField();
        jLabel27 = new javax.swing.JLabel();
        ReturnBookISBN = new javax.swing.JTextField();
        ReurnBook = new javax.swing.JButton();
        jPanel10 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(51, 51, 51));
        setResizable(false);
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jPanel2.setBackground(new java.awt.Color(51, 0, 204));

        jLabel1.setFont(new java.awt.Font("sansserif", 1, 30)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setText("Library Management System");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(147, 147, 147)
                .addComponent(jLabel1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(8, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addContainerGap())
        );

        getContentPane().add(jPanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(-1, 0, 800, 50));

        jPanel1.setBackground(new java.awt.Color(153, 51, 255));

        jButton1.setBackground(new java.awt.Color(102, 102, 255));
        jButton1.setForeground(new java.awt.Color(255, 255, 255));
        jButton1.setText("Add Book");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton2.setBackground(new java.awt.Color(102, 102, 255));
        jButton2.setForeground(new java.awt.Color(255, 255, 255));
        jButton2.setText("Add Member");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton3.setBackground(new java.awt.Color(102, 102, 255));
        jButton3.setForeground(new java.awt.Color(255, 255, 255));
        jButton3.setText("Barrow Book");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jButton4.setBackground(new java.awt.Color(102, 102, 255));
        jButton4.setForeground(new java.awt.Color(255, 255, 255));
        jButton4.setText("Return Book");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jButton5.setBackground(new java.awt.Color(102, 102, 255));
        jButton5.setForeground(new java.awt.Color(255, 255, 255));
        jButton5.setText("Available Book");
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        jButton7.setBackground(new java.awt.Color(102, 102, 255));
        jButton7.setForeground(new java.awt.Color(255, 255, 255));
        jButton7.setText("Home");
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });

        jButton6.setBackground(new java.awt.Color(102, 102, 255));
        jButton6.setForeground(new java.awt.Color(255, 255, 255));
        jButton6.setText("Add Admin");
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });

        jButton8.setBackground(new java.awt.Color(102, 102, 255));
        jButton8.setForeground(new java.awt.Color(255, 255, 255));
        jButton8.setText("About");
        jButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });

        jButton9.setBackground(new java.awt.Color(255, 51, 51));
        jButton9.setForeground(new java.awt.Color(255, 255, 255));
        jButton9.setText("Logout");
        jButton9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton9ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jButton3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jButton4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(jButton6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jButton5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jButton8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jButton9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(67, Short.MAX_VALUE)
                .addComponent(jButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton6, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton8, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton9, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        getContentPane().add(jPanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 130, 488));

        Home.setBackground(new java.awt.Color(255, 255, 102));

        jLabel28.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel28.setForeground(new java.awt.Color(0, 0, 255));
        jLabel28.setText("Total Book : ");

        jLabel29.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel29.setForeground(new java.awt.Color(0, 0, 255));
        jLabel29.setText("Total Members : ");

        jLabel31.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel31.setForeground(new java.awt.Color(0, 0, 255));
        jLabel31.setText("Total Barrow Books : ");

        TotalBooks.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        TotalBooks.setForeground(new java.awt.Color(0, 0, 0));
        TotalBooks.setText("11");

        TotalBarrow.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        TotalBarrow.setForeground(new java.awt.Color(0, 0, 0));
        TotalBarrow.setText("11");

        TotalMembers.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        TotalMembers.setForeground(new java.awt.Color(0, 0, 0));
        TotalMembers.setText("11");

        jLabel30.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel30.setForeground(new java.awt.Color(0, 0, 255));
        jLabel30.setText("Total Available Book : ");

        TotalavailableBook.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        TotalavailableBook.setForeground(new java.awt.Color(0, 0, 0));
        TotalavailableBook.setText("11");

        javax.swing.GroupLayout HomeLayout = new javax.swing.GroupLayout(Home);
        Home.setLayout(HomeLayout);
        HomeLayout.setHorizontalGroup(
            HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(HomeLayout.createSequentialGroup()
                .addGap(104, 104, 104)
                .addGroup(HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(HomeLayout.createSequentialGroup()
                        .addComponent(jLabel31)
                        .addGap(18, 18, 18)
                        .addComponent(TotalBarrow))
                    .addGroup(HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(HomeLayout.createSequentialGroup()
                            .addComponent(jLabel29)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(TotalMembers))
                        .addGroup(HomeLayout.createSequentialGroup()
                            .addComponent(jLabel30)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(TotalavailableBook))
                        .addGroup(HomeLayout.createSequentialGroup()
                            .addComponent(jLabel28)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(TotalBooks))))
                .addContainerGap(276, Short.MAX_VALUE))
        );
        HomeLayout.setVerticalGroup(
            HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(HomeLayout.createSequentialGroup()
                .addGap(71, 71, 71)
                .addGroup(HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel28)
                    .addComponent(TotalBooks))
                .addGap(26, 26, 26)
                .addGroup(HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel30)
                    .addComponent(TotalavailableBook))
                .addGap(31, 31, 31)
                .addGroup(HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel29)
                    .addComponent(TotalMembers))
                .addGap(29, 29, 29)
                .addGroup(HomeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel31)
                    .addComponent(TotalBarrow))
                .addContainerGap(192, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("tab1", Home);

        addBook.setBackground(new java.awt.Color(153, 255, 153));

        Title.setBackground(new java.awt.Color(255, 255, 255));
        Title.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        Title.setForeground(new java.awt.Color(0, 0, 0));

        jLabel8.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(0, 0, 0));
        jLabel8.setText("Title         :");

        jLabel9.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(0, 0, 0));
        jLabel9.setText("Category :");

        SecondName.setBackground(new java.awt.Color(255, 255, 255));
        SecondName.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        SecondName.setForeground(new java.awt.Color(0, 0, 0));

        jLabel10.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(0, 0, 0));
        jLabel10.setText("Second Name");

        FirstName.setBackground(new java.awt.Color(255, 255, 255));
        FirstName.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        FirstName.setForeground(new java.awt.Color(0, 0, 0));

        jLabel11.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(0, 0, 0));
        jLabel11.setText("First Name");

        jLabel12.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(0, 0, 0));
        jLabel12.setText("Author :");

        Categori.setBackground(new java.awt.Color(255, 255, 255));
        Categori.setForeground(new java.awt.Color(0, 0, 0));
        Categori.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        Categori.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CategoriActionPerformed(evt);
            }
        });

        jLabel13.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(0, 0, 0));
        jLabel13.setText("Publisher :");

        Publisher.setBackground(new java.awt.Color(255, 255, 255));
        Publisher.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        Publisher.setForeground(new java.awt.Color(0, 0, 0));

        jLabel14.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel14.setForeground(new java.awt.Color(0, 0, 0));
        jLabel14.setText("PB Year   :");

        PublishYear.setBackground(new java.awt.Color(255, 255, 255));
        PublishYear.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        PublishYear.setForeground(new java.awt.Color(0, 0, 0));
        PublishYear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PublishYearActionPerformed(evt);
            }
        });

        jLabel15.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel15.setForeground(new java.awt.Color(0, 0, 0));
        jLabel15.setText("ISBN        :");

        Isbn.setBackground(new java.awt.Color(255, 255, 255));
        Isbn.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        Isbn.setForeground(new java.awt.Color(0, 0, 0));

        jLabel16.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(0, 0, 0));
        jLabel16.setText("Quantity :");

        quantity.setBackground(new java.awt.Color(255, 255, 255));
        quantity.setFont(new java.awt.Font("sansserif", 0, 14)); // NOI18N
        quantity.setForeground(new java.awt.Color(0, 0, 0));

        AddBook.setBackground(new java.awt.Color(0, 102, 255));
        AddBook.setForeground(new java.awt.Color(255, 255, 255));
        AddBook.setText("Add Book");
        AddBook.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddBookActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout addBookLayout = new javax.swing.GroupLayout(addBook);
        addBook.setLayout(addBookLayout);
        addBookLayout.setHorizontalGroup(
            addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addBookLayout.createSequentialGroup()
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(addBookLayout.createSequentialGroup()
                        .addGap(38, 38, 38)
                        .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(addBookLayout.createSequentialGroup()
                                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel12)
                                    .addComponent(jLabel9, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(addBookLayout.createSequentialGroup()
                                        .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(FirstName, javax.swing.GroupLayout.PREFERRED_SIZE, 231, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(jLabel11))
                                        .addGap(18, 18, 18)
                                        .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel10)
                                            .addComponent(SecondName, javax.swing.GroupLayout.PREFERRED_SIZE, 227, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addComponent(Title, javax.swing.GroupLayout.PREFERRED_SIZE, 476, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(Categori, javax.swing.GroupLayout.PREFERRED_SIZE, 231, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(addBookLayout.createSequentialGroup()
                                .addComponent(jLabel14)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(PublishYear, javax.swing.GroupLayout.PREFERRED_SIZE, 185, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(addBookLayout.createSequentialGroup()
                                .addComponent(jLabel13)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(Publisher, javax.swing.GroupLayout.PREFERRED_SIZE, 253, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(addBookLayout.createSequentialGroup()
                        .addGap(247, 247, 247)
                        .addComponent(AddBook, javax.swing.GroupLayout.PREFERRED_SIZE, 129, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(addBookLayout.createSequentialGroup()
                        .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(addBookLayout.createSequentialGroup()
                                .addGap(38, 38, 38)
                                .addComponent(jLabel16))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, addBookLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jLabel15)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(Isbn, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(quantity, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(44, Short.MAX_VALUE))
        );
        addBookLayout.setVerticalGroup(
            addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addBookLayout.createSequentialGroup()
                .addGap(34, 34, 34)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Title, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel11)
                    .addComponent(jLabel12))
                .addGap(6, 6, 6)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(SecondName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(FirstName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(7, 7, 7)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Categori, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(Publisher, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(PublishYear, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14))
                .addGap(11, 11, 11)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(Isbn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(quantity, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16))
                .addGap(28, 28, 28)
                .addComponent(AddBook, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(81, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("tab2", addBook);

        addMember.setBackground(new java.awt.Color(255, 255, 153));

        jLabel17.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(0, 0, 0));
        jLabel17.setText("First Name :");

        MemberFirstName.setBackground(new java.awt.Color(255, 255, 255));
        MemberFirstName.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        MemberFirstName.setForeground(new java.awt.Color(0, 0, 0));

        jLabel18.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel18.setForeground(new java.awt.Color(0, 0, 0));
        jLabel18.setText("Last_Name :");

        MemberLastName.setBackground(new java.awt.Color(255, 255, 255));
        MemberLastName.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        MemberLastName.setForeground(new java.awt.Color(0, 0, 0));

        jLabel19.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(0, 0, 0));
        jLabel19.setText("Address      :");

        MemberAdress.setBackground(new java.awt.Color(255, 255, 255));
        MemberAdress.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        MemberAdress.setForeground(new java.awt.Color(0, 0, 0));

        jLabel20.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel20.setForeground(new java.awt.Color(0, 0, 0));
        jLabel20.setText("Phone No   :");

        MemberPhone.setBackground(new java.awt.Color(255, 255, 255));
        MemberPhone.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        MemberPhone.setForeground(new java.awt.Color(0, 0, 0));

        jLabel21.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(0, 0, 0));
        jLabel21.setText("Email          :");

        MemberEmail.setBackground(new java.awt.Color(255, 255, 255));
        MemberEmail.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        MemberEmail.setForeground(new java.awt.Color(0, 0, 0));

        AddMember.setBackground(new java.awt.Color(0, 153, 0));
        AddMember.setFont(new java.awt.Font("sansserif", 0, 24)); // NOI18N
        AddMember.setForeground(new java.awt.Color(255, 255, 255));
        AddMember.setText("Register");
        AddMember.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddMemberActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout addMemberLayout = new javax.swing.GroupLayout(addMember);
        addMember.setLayout(addMemberLayout);
        addMemberLayout.setHorizontalGroup(
            addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addMemberLayout.createSequentialGroup()
                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(addMemberLayout.createSequentialGroup()
                        .addGap(43, 43, 43)
                        .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addGroup(addMemberLayout.createSequentialGroup()
                                    .addComponent(jLabel20, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(MemberPhone, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(addMemberLayout.createSequentialGroup()
                                        .addComponent(jLabel19)
                                        .addGap(18, 18, 18)
                                        .addComponent(MemberAdress, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addGroup(addMemberLayout.createSequentialGroup()
                                            .addComponent(jLabel18)
                                            .addGap(18, 18, 18)
                                            .addComponent(MemberLastName, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(addMemberLayout.createSequentialGroup()
                                            .addComponent(jLabel17)
                                            .addGap(18, 18, 18)
                                            .addComponent(MemberFirstName, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                            .addGroup(addMemberLayout.createSequentialGroup()
                                .addComponent(jLabel21)
                                .addGap(18, 18, 18)
                                .addComponent(MemberEmail, javax.swing.GroupLayout.PREFERRED_SIZE, 320, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(addMemberLayout.createSequentialGroup()
                        .addGap(217, 217, 217)
                        .addComponent(AddMember, javax.swing.GroupLayout.PREFERRED_SIZE, 166, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(148, Short.MAX_VALUE))
        );
        addMemberLayout.setVerticalGroup(
            addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addMemberLayout.createSequentialGroup()
                .addGap(46, 46, 46)
                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel17)
                    .addComponent(MemberFirstName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(MemberLastName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19)
                    .addComponent(MemberAdress, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(MemberPhone, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addMemberLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel21)
                    .addComponent(MemberEmail, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 97, Short.MAX_VALUE)
                .addComponent(AddMember, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(67, 67, 67))
        );

        jTabbedPane1.addTab("tab3", addMember);

        addAdmin.setBackground(new java.awt.Color(204, 204, 255));

        jLabel22.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel22.setForeground(new java.awt.Color(0, 0, 0));
        jLabel22.setText("Username :");

        AdminUserName.setBackground(new java.awt.Color(255, 255, 255));
        AdminUserName.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        AdminUserName.setForeground(new java.awt.Color(0, 0, 0));

        AdminPassword.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        AdminPassword.setForeground(new java.awt.Color(0, 0, 0));
        AdminPassword.setText("Password  :");

        AdminPass.setBackground(new java.awt.Color(255, 255, 255));
        AdminPass.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        AdminPass.setForeground(new java.awt.Color(0, 0, 0));

        AdminEmail.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        AdminEmail.setForeground(new java.awt.Color(0, 0, 0));
        AdminEmail.setText("Email        :");

        Admin_Email.setBackground(new java.awt.Color(255, 255, 255));
        Admin_Email.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        Admin_Email.setForeground(new java.awt.Color(0, 0, 0));

        RegAdmin.setBackground(new java.awt.Color(0, 102, 0));
        RegAdmin.setFont(new java.awt.Font("sansserif", 1, 24)); // NOI18N
        RegAdmin.setForeground(new java.awt.Color(255, 255, 255));
        RegAdmin.setText("Register");
        RegAdmin.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RegAdminActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout addAdminLayout = new javax.swing.GroupLayout(addAdmin);
        addAdmin.setLayout(addAdminLayout);
        addAdminLayout.setHorizontalGroup(
            addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addAdminLayout.createSequentialGroup()
                .addGap(70, 70, 70)
                .addGroup(addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(addAdminLayout.createSequentialGroup()
                        .addComponent(AdminPassword)
                        .addGap(18, 18, 18)
                        .addComponent(AdminPass, javax.swing.GroupLayout.PREFERRED_SIZE, 299, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(addAdminLayout.createSequentialGroup()
                        .addComponent(jLabel22)
                        .addGap(18, 18, 18)
                        .addComponent(AdminUserName, javax.swing.GroupLayout.PREFERRED_SIZE, 299, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(addAdminLayout.createSequentialGroup()
                        .addComponent(AdminEmail)
                        .addGap(18, 18, 18)
                        .addGroup(addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(Admin_Email, javax.swing.GroupLayout.PREFERRED_SIZE, 299, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(RegAdmin, javax.swing.GroupLayout.PREFERRED_SIZE, 183, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(152, Short.MAX_VALUE))
        );
        addAdminLayout.setVerticalGroup(
            addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addAdminLayout.createSequentialGroup()
                .addGap(69, 69, 69)
                .addGroup(addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(AdminUserName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel22))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(AdminPass, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AdminPassword))
                .addGap(18, 18, 18)
                .addGroup(addAdminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(Admin_Email, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(AdminEmail))
                .addGap(62, 62, 62)
                .addComponent(RegAdmin, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(152, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("tab4", addAdmin);

        availableBooks.setBackground(new java.awt.Color(51, 255, 204));

        BookTable.setAutoCreateRowSorter(true);
        BookTable.setBackground(new java.awt.Color(255, 255, 255));
        BookTable.setForeground(new java.awt.Color(0, 0, 0));
        BookTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "ID", "Title", "Category", "Quantity"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.Object.class, java.lang.Object.class, java.lang.Integer.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        BookTable.setAlignmentY(2.0F);
        BookTable.setColumnSelectionAllowed(true);
        jScrollPane1.setViewportView(BookTable);
        BookTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        if (BookTable.getColumnModel().getColumnCount() > 0) {
            BookTable.getColumnModel().getColumn(0).setResizable(false);
            BookTable.getColumnModel().getColumn(1).setResizable(false);
            BookTable.getColumnModel().getColumn(2).setResizable(false);
            BookTable.getColumnModel().getColumn(3).setResizable(false);
        }

        javax.swing.GroupLayout availableBooksLayout = new javax.swing.GroupLayout(availableBooks);
        availableBooks.setLayout(availableBooksLayout);
        availableBooksLayout.setHorizontalGroup(
            availableBooksLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(availableBooksLayout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 623, Short.MAX_VALUE)
                .addGap(25, 25, 25))
        );
        availableBooksLayout.setVerticalGroup(
            availableBooksLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(availableBooksLayout.createSequentialGroup()
                .addGap(32, 32, 32)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 347, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(66, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("tab5", availableBooks);

        barrowBook.setBackground(new java.awt.Color(102, 153, 255));

        jLabel23.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel23.setForeground(new java.awt.Color(0, 0, 0));
        jLabel23.setText("Member ID  :");

        BrrowMemberID.setBackground(new java.awt.Color(255, 255, 255));
        BrrowMemberID.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        BrrowMemberID.setForeground(new java.awt.Color(0, 0, 0));
        BrrowMemberID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BrrowMemberIDActionPerformed(evt);
            }
        });

        jLabel24.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel24.setForeground(new java.awt.Color(0, 0, 0));
        jLabel24.setText("ISBN            :");

        BarrowBookISBN.setBackground(new java.awt.Color(255, 255, 255));
        BarrowBookISBN.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        BarrowBookISBN.setForeground(new java.awt.Color(0, 0, 0));
        BarrowBookISBN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BarrowBookISBNActionPerformed(evt);
            }
        });

        BookReturnDate.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        BookReturnDate.setForeground(new java.awt.Color(255, 255, 255));

        jLabel26.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        jLabel26.setForeground(new java.awt.Color(255, 255, 255));
        jLabel26.setText("Member Need Submit Book Befor :");

        ReleaseBook.setBackground(new java.awt.Color(51, 102, 0));
        ReleaseBook.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        ReleaseBook.setForeground(new java.awt.Color(255, 255, 255));
        ReleaseBook.setText("Release Book");
        ReleaseBook.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReleaseBookActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout barrowBookLayout = new javax.swing.GroupLayout(barrowBook);
        barrowBook.setLayout(barrowBookLayout);
        barrowBookLayout.setHorizontalGroup(
            barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(barrowBookLayout.createSequentialGroup()
                .addGroup(barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(barrowBookLayout.createSequentialGroup()
                        .addGap(54, 54, 54)
                        .addGroup(barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(barrowBookLayout.createSequentialGroup()
                                .addComponent(jLabel24)
                                .addGap(31, 31, 31)
                                .addComponent(BarrowBookISBN, javax.swing.GroupLayout.PREFERRED_SIZE, 267, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(barrowBookLayout.createSequentialGroup()
                                .addComponent(jLabel23)
                                .addGap(31, 31, 31)
                                .addComponent(BrrowMemberID, javax.swing.GroupLayout.PREFERRED_SIZE, 267, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(barrowBookLayout.createSequentialGroup()
                        .addGap(218, 218, 218)
                        .addComponent(ReleaseBook, javax.swing.GroupLayout.PREFERRED_SIZE, 185, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, barrowBookLayout.createSequentialGroup()
                .addGap(65, 65, 65)
                .addComponent(jLabel26)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 142, Short.MAX_VALUE)
                .addComponent(BookReturnDate)
                .addGap(115, 115, 115))
        );
        barrowBookLayout.setVerticalGroup(
            barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(barrowBookLayout.createSequentialGroup()
                .addGap(65, 65, 65)
                .addGroup(barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel23)
                    .addComponent(BrrowMemberID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel24)
                    .addComponent(BarrowBookISBN, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(barrowBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(barrowBookLayout.createSequentialGroup()
                        .addGap(61, 61, 61)
                        .addComponent(BookReturnDate))
                    .addGroup(barrowBookLayout.createSequentialGroup()
                        .addGap(53, 53, 53)
                        .addComponent(jLabel26)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 106, Short.MAX_VALUE)
                .addComponent(ReleaseBook, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(86, 86, 86))
        );

        jTabbedPane1.addTab("tab6", barrowBook);

        returnBook.setBackground(new java.awt.Color(255, 153, 102));

        jLabel25.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel25.setForeground(new java.awt.Color(0, 0, 0));
        jLabel25.setText("Member_ID  :");

        ReturnMemberID.setBackground(new java.awt.Color(255, 255, 255));
        ReturnMemberID.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        ReturnMemberID.setForeground(new java.awt.Color(0, 0, 0));

        jLabel27.setFont(new java.awt.Font("sansserif", 1, 20)); // NOI18N
        jLabel27.setForeground(new java.awt.Color(0, 0, 0));
        jLabel27.setText("ISBN            :");

        ReturnBookISBN.setBackground(new java.awt.Color(255, 255, 255));
        ReturnBookISBN.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        ReturnBookISBN.setForeground(new java.awt.Color(0, 0, 0));

        ReurnBook.setBackground(new java.awt.Color(51, 102, 0));
        ReurnBook.setFont(new java.awt.Font("sansserif", 1, 18)); // NOI18N
        ReurnBook.setForeground(new java.awt.Color(255, 255, 255));
        ReurnBook.setText("Add Book");
        ReurnBook.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReurnBookActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout returnBookLayout = new javax.swing.GroupLayout(returnBook);
        returnBook.setLayout(returnBookLayout);
        returnBookLayout.setHorizontalGroup(
            returnBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(returnBookLayout.createSequentialGroup()
                .addGap(69, 69, 69)
                .addGroup(returnBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(returnBookLayout.createSequentialGroup()
                        .addComponent(jLabel25)
                        .addGap(18, 18, 18)
                        .addComponent(ReturnMemberID, javax.swing.GroupLayout.PREFERRED_SIZE, 201, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(returnBookLayout.createSequentialGroup()
                        .addComponent(jLabel27)
                        .addGap(18, 18, 18)
                        .addGroup(returnBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ReurnBook)
                            .addComponent(ReturnBookISBN))))
                .addContainerGap(233, Short.MAX_VALUE))
        );
        returnBookLayout.setVerticalGroup(
            returnBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(returnBookLayout.createSequentialGroup()
                .addGap(84, 84, 84)
                .addGroup(returnBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel25)
                    .addComponent(ReturnMemberID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(returnBookLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel27)
                    .addComponent(ReturnBookISBN, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(70, 70, 70)
                .addComponent(ReurnBook)
                .addContainerGap(195, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("tab7", returnBook);

        jPanel10.setBackground(new java.awt.Color(255, 255, 255));

        jLabel2.setBackground(new java.awt.Color(255, 255, 255));
        jLabel2.setFont(new java.awt.Font("TeXGyreTermes", 1, 24)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(0, 0, 0));
        jLabel2.setText("Bachelor Of Information Technology");

        jLabel3.setBackground(new java.awt.Color(255, 255, 255));
        jLabel3.setFont(new java.awt.Font("TeXGyreTermes", 1, 24)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(0, 0, 0));
        jLabel3.setText("University Of Moratuwa");

        jLabel4.setBackground(new java.awt.Color(255, 255, 255));
        jLabel4.setFont(new java.awt.Font("TeXGyreTermes", 1, 18)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(0, 0, 0));
        jLabel4.setText("G.H.S.K WIJESOORIYA");

        jLabel5.setBackground(new java.awt.Color(255, 255, 255));
        jLabel5.setFont(new java.awt.Font("TeXGyreTermes", 1, 18)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(0, 0, 0));
        jLabel5.setText("E2245634");

        jLabel6.setBackground(new java.awt.Color(255, 255, 255));
        jLabel6.setFont(new java.awt.Font("TeXGyreTermes", 1, 18)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(0, 0, 0));
        jLabel6.setText("( Library Management System )");

        jLabel7.setBackground(new java.awt.Color(255, 255, 255));
        jLabel7.setFont(new java.awt.Font("TeXGyreTermes", 1, 18)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(0, 0, 0));
        jLabel7.setText("- ICT Project -");

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGap(121, 121, 121)
                        .addComponent(jLabel2))
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGap(71, 71, 71)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5)
                            .addComponent(jLabel4)))
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGap(181, 181, 181)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel6)
                            .addComponent(jLabel3))))
                .addContainerGap(167, Short.MAX_VALUE))
            .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel10Layout.createSequentialGroup()
                    .addGap(247, 247, 247)
                    .addComponent(jLabel7)
                    .addContainerGap(308, Short.MAX_VALUE)))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGap(60, 60, 60)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 100, Short.MAX_VALUE)
                .addComponent(jLabel6)
                .addGap(22, 22, 22)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel4)
                .addGap(89, 89, 89))
            .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel10Layout.createSequentialGroup()
                    .addGap(172, 172, 172)
                    .addComponent(jLabel7)
                    .addContainerGap(245, Short.MAX_VALUE)))
        );

        jTabbedPane1.addTab("tab8", jPanel10);

        getContentPane().add(jTabbedPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 10, 670, 480));

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(1);
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(2);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(5);
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(6);
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(4);
        loadAvailableBooks(); 
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(0);
        loadTotals();
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        // TODO add your handling code here:
         jTabbedPane1.setSelectedIndex(3);
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
        // TODO add your handling code here:
        jTabbedPane1.setSelectedIndex(7);
    }//GEN-LAST:event_jButton8ActionPerformed

    private void CategoriActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CategoriActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_CategoriActionPerformed

    private void AddBookActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddBookActionPerformed
        // TODO add your handling code here:
        addBook();
    }//GEN-LAST:event_AddBookActionPerformed

    private void AddMemberActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddMemberActionPerformed
        // TODO add your handling code here:
        addMember();
    }//GEN-LAST:event_AddMemberActionPerformed

    private void RegAdminActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RegAdminActionPerformed
        // TODO add your handling code here:
         addAdmin();
         
    }//GEN-LAST:event_RegAdminActionPerformed

    private void jButton9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton9ActionPerformed
        // TODO add your handling code here:
        
        logoutActionPerformed(evt);
        
    }//GEN-LAST:event_jButton9ActionPerformed

    private void BrrowMemberIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BrrowMemberIDActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_BrrowMemberIDActionPerformed

    private void BarrowBookISBNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_BarrowBookISBNActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_BarrowBookISBNActionPerformed

    private void ReleaseBookActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReleaseBookActionPerformed
        try {
            // TODO add your handling code here:
            borrowBook();
            
        } catch (SQLException ex) {
            Logger.getLogger(Dashboard.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_ReleaseBookActionPerformed

    private void ReurnBookActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReurnBookActionPerformed
        // TODO add your handling code here:
        returnBook();
        
    }//GEN-LAST:event_ReurnBookActionPerformed

    private void PublishYearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PublishYearActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_PublishYearActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Dashboard.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Dashboard.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Dashboard.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Dashboard.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Dashboard().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddBook;
    private javax.swing.JButton AddMember;
    private javax.swing.JLabel AdminEmail;
    private javax.swing.JTextField AdminPass;
    private javax.swing.JLabel AdminPassword;
    private javax.swing.JTextField AdminUserName;
    private javax.swing.JTextField Admin_Email;
    private javax.swing.JTextField BarrowBookISBN;
    private javax.swing.JLabel BookReturnDate;
    private javax.swing.JTable BookTable;
    private javax.swing.JTextField BrrowMemberID;
    private javax.swing.JComboBox<String> Categori;
    private javax.swing.JTextField FirstName;
    private javax.swing.JPanel Home;
    private javax.swing.JTextField Isbn;
    private javax.swing.JTextField MemberAdress;
    private javax.swing.JTextField MemberEmail;
    private javax.swing.JTextField MemberFirstName;
    private javax.swing.JTextField MemberLastName;
    private javax.swing.JTextField MemberPhone;
    private javax.swing.JTextField PublishYear;
    private javax.swing.JTextField Publisher;
    private javax.swing.JButton RegAdmin;
    private javax.swing.JButton ReleaseBook;
    private javax.swing.JTextField ReturnBookISBN;
    private javax.swing.JTextField ReturnMemberID;
    private javax.swing.JButton ReurnBook;
    private javax.swing.JTextField SecondName;
    private javax.swing.JTextField Title;
    private javax.swing.JLabel TotalBarrow;
    private javax.swing.JLabel TotalBooks;
    private javax.swing.JLabel TotalMembers;
    private javax.swing.JLabel TotalavailableBook;
    private javax.swing.JPanel addAdmin;
    private javax.swing.JPanel addBook;
    private javax.swing.JPanel addMember;
    private javax.swing.JPanel availableBooks;
    private javax.swing.JPanel barrowBook;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTextField quantity;
    private javax.swing.JPanel returnBook;
    // End of variables declaration//GEN-END:variables
}
