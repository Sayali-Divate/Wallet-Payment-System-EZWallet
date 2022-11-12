package com.ezwallet.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ezwallet.exception.BankAccountException;
import com.ezwallet.exception.BeneficiaryException;
import com.ezwallet.exception.CustomerException;
import com.ezwallet.exception.WalletException;
import com.ezwallet.model.BankAccount;
import com.ezwallet.model.Beneficiary;
import com.ezwallet.model.CurrentUserSession;
import com.ezwallet.model.Customer;
import com.ezwallet.model.Transaction;
import com.ezwallet.model.Wallet;
import com.ezwallet.repository.BankAccountDao;
import com.ezwallet.repository.BeneficiaryDao;
import com.ezwallet.repository.CurrentSessionDao;
import com.ezwallet.repository.CustomerRepository;
import com.ezwallet.repository.TransactionRepo;
import com.ezwallet.repository.WalletRepository;

@Service
public class WalletServiceImpl implements WalletService{
	
	@Autowired
	public CustomerRepository customerRepo; 
	
	@Autowired
	public WalletRepository walletRepo;
	
	@Autowired
	public CurrentSessionDao user;
	
	@Autowired
	public BankAccountDao bankRepo;
	
	@Autowired
	public TransactionDao transactionServe;
	
	@Autowired
	public BeneficiaryDao beneficiaryRepo;
	
	@Override
	public Customer createCustomerAccount(Customer customer) throws CustomerException {
		
		List<Customer> cust = customerRepo.findCustomerByMobile(customer.getMobileNumber());
		
		if(!cust.isEmpty()) throw new CustomerException("This mobile number is already registered with EZWallet");
		
		else  {		
			Wallet wallet = new Wallet();
			wallet.setBalance(BigDecimal.valueOf(0));
			wallet.setCustomer(customer);
			return walletRepo.save(wallet).getCustomer();
			
		}
		
	}

	@Override
	public BigDecimal showWalletBalance(String mobileNumber) throws CustomerException {
		
		List<Customer> cust = customerRepo.findCustomerByMobile(mobileNumber);
		
		if(cust.isEmpty()) throw new CustomerException("Customer does not exist. Please create account");
		
		else {
			
			Integer id = cust.get(0).getCustomerId();
			Wallet wl = walletRepo.showWalletDetails(id);
			
			return wl.getBalance();
		}
		
		
	}

	@Override
	public String addMoneyFromBankToWallet(Integer accountNo, Double amount) throws BankAccountException, CustomerException {
		
		
//		checking if user logged in or not
		List<CurrentUserSession> currUser = user.findAll();
		
		if(currUser.isEmpty()) throw new CustomerException("Please Log in to continue");
		
		
//		Checking bank Accounts
		Integer id = currUser.get(0).getUserId();
		Wallet wallet = walletRepo.showWalletDetails(id);
		
		List<BankAccount> accounts = bankRepo.findAllByWallet(wallet);
		
		if(accounts.isEmpty()) throw new BankAccountException("Add bank account for transaction");
		
		BankAccount acct = null;
		boolean flag=false;
		
		for(BankAccount b : accounts) {
			if(b.getAccountNo()==accountNo) {
				acct=b;
				flag=true;
				break;
			}
		}
		
		if(!flag) throw new BankAccountException("Bank account number does not match the data of saved accounts");
				
		if(acct.getBalance() < amount) throw new BankAccountException("Insufficient balance in account");
		
		acct.setBalance(acct.getBalance()-amount);
		wallet.setBalance(wallet.getBalance().add(BigDecimal.valueOf(amount)));
		
		bankRepo.save(acct);
		
		Transaction trans = new Transaction();
		trans.setTransactionType("Bank transfer");
		trans.setTransactionDate(LocalDate.now());
		trans.setAmount(amount);
		trans.setWallet(wallet);
		trans.setDescription("transferred from bank "+acct.getBankName()+" to wallet");
		
		transactionServe.addTransaction(trans);	
		
		
		return "Your bank account no "+ accountNo +" debited for "+ amount +" Rs" ;
	}

	@Override
	public String fundTransferToAnotherMobileNumber(String targetMobile, String Name, BigDecimal amount)
			throws WalletException, CustomerException {
	
		List<CurrentUserSession> currUser = user.findAll();
		
		if(currUser.isEmpty()) throw new CustomerException("Please Log in to continue");
		
		Integer id = currUser.get(0).getUserId();
		Wallet wallet = walletRepo.showWalletDetails(id);
		
		Beneficiary beneficiary = new Beneficiary(targetMobile, Name, wallet);
		
		List<Beneficiary> beneficiaries = beneficiaryRepo.findByWallet(wallet.getWalletId());
		
		if(!beneficiaries.contains(beneficiary)) beneficiaryRepo.save(beneficiary);
		
				
		List<Customer> customers =  customerRepo.findCustomerByMobile(targetMobile);
		
		if(customers.isEmpty()) throw new CustomerException("Customer with mobile number "+ targetMobile+" does not exist on EZWallet");
		
		Wallet targetWallet = walletRepo.showWalletDetails(customers.get(0).getCustomerId());
		
		if(wallet.getBalance().compareTo(amount)<0) throw new WalletException("Add more amount in wallet for transaction");
		
		targetWallet.setBalance(targetWallet.getBalance().add(amount));
		walletRepo.save(targetWallet);
		
		wallet.setBalance(wallet.getBalance().subtract(amount));
		walletRepo.save(wallet);
		
		
		Transaction trans = new Transaction();
		trans.setTransactionType("Bank transfer");
		trans.setTransactionDate(LocalDate.now());
		trans.setAmount(amount.doubleValue());
		trans.setWallet(wallet);
		trans.setDescription("Amount transfered to "+ targetMobile);
		
		transactionServe.addTransaction(trans);	
		
		
		
		return "Fund Transfered successfully...";
	}
	
	
	
	

}
