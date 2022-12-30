package com.example.library.studentlibrary.services;

import com.example.library.studentlibrary.models.*;
import com.example.library.studentlibrary.repositories.BookRepository;
import com.example.library.studentlibrary.repositories.CardRepository;
import com.example.library.studentlibrary.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService {

    @Autowired
    BookRepository bookRepository5;

    @Autowired
    CardRepository cardRepository5;

    @Autowired
    TransactionRepository transactionRepository5;

    @Value("${books.max_allowed}")
    int max_allowed_books;

    @Value("${books.max_allowed_days}")
    int getMax_allowed_days;

    @Value("${books.fine.per_day}")
    int fine_per_day;

    public String issueBook(int cardId, int bookId) throws Exception {
        //check whether bookId and cardId already exist
        //conditions required for successful transaction of issue book:
        //1. book is present and available
        // If it fails: throw new Exception("Book is either unavailable or not present");
        //2. card is present and activated
        // If it fails: throw new Exception("Card is invalid");
        //3. number of books issued against the card is strictly less than max_allowed_books
        // If it fails: throw new Exception("Book limit has reached for this card");
        //If the transaction is successful, save the transaction to the list of transactions and return the id

        //Note that the error message should match exactly in all cases
        Book book = bookRepository5.findById(bookId).get();
        Card card = cardRepository5.findById(cardId).get();

        Transaction transaction = Transaction.builder().book(book).card(card).build();
        transaction.setIssueOperation(true);
        if(book==null || book.isAvailable()==false)
        {
            transaction.setTransactionStatus(TransactionStatus.FAILED);

            transactionRepository5.save(transaction);
            throw new Exception("Book is either unavailable or not present");

        }
        if(card==null || card.getCardStatus().equals(CardStatus.DEACTIVATED)){
            transaction.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction);

            throw new Exception("Card is invalid");
        }
        
        if(card.getBooks().size()>=max_allowed_books){

            transaction.setTransactionStatus(TransactionStatus.FAILED);
            transactionRepository5.save(transaction);

            throw new Exception("Book limit has reached for this card");
        }

        book.setCard(card);
        book.setAvailable(false);
        card.getBooks().add(book);

        bookRepository5.updateBook(book);

        transaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        transactionRepository5.save(transaction);



       return transaction.getTransactionId(); //return transactionId instead
    }

    public Transaction returnBook(int cardId, int bookId) throws Exception{

        List<Transaction> transactions = transactionRepository5.find(cardId, bookId,TransactionStatus.SUCCESSFUL, true);
        Transaction transaction = transactions.get(transactions.size() - 1);

        //for the given transaction calculate the fine amount considering the book has been returned exactly when this function is called
        //make the book available for other users
        //make a new transaction for return book which contains the fine amount as well
        long issuedtime = transaction.getTransactionDate().getTime();

        long timeDifference = Math.abs(System.currentTimeMillis()-issuedtime);
        
        long totalDays = TimeUnit.DAYS.convert(timeDifference, TimeUnit.MILLISECONDS);

        int fine=0;
        if(totalDays>getMax_allowed_days){
            fine = (int)((totalDays-getMax_allowed_days)*fine_per_day);
        }
        Book book = transaction.getBook();
        book.setAvailable(true);
        book.setCard(null);

        bookRepository5.updateBook(book);

        Transaction newTransaction = Transaction.builder().book(transaction.getBook()).card(transaction.getCard()).build();

        
        newTransaction.setFineAmount(fine);
        newTransaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);
        newTransaction.setIssueOperation(false);
        transactionRepository5.save(newTransaction);
        // Transaction returnBookTransaction  = null;
        return newTransaction; //return the transaction after updating all details
    }
}