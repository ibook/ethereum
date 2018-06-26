package cn.netkiller.wallet.restful;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import cn.netkiller.wallet.domain.TransactionHistory;
import cn.netkiller.wallet.domain.TransactionPostion;
import cn.netkiller.wallet.pojo.JsonRpc;
import cn.netkiller.wallet.pojo.TokenTransaction;
import cn.netkiller.wallet.pojo.TokenTransactionResponse;
import cn.netkiller.wallet.pojo.Transaction;
import cn.netkiller.wallet.pojo.TransactionResponse;
import cn.netkiller.wallet.repository.TransactionHistoryRepository;
import cn.netkiller.wallet.repository.TransactionPostionRepository;
import cn.netkiller.wallet.service.EthereumWallet;

@RestController
public class HistoryRestController {
	private static final Logger logger = LoggerFactory.getLogger(HistoryRestController.class);

	@Autowired
	TransactionHistoryRepository transactionHistoryRepository;

	@Autowired
	TransactionPostionRepository transactionPostionRepository;

	@Value("${ethereum.developer.apis}")
	private String ethereumDeveloperApis;

	@Autowired
	private EthereumWallet ethereumWallet;

	public HistoryRestController() {
		// TODO Auto-generated constructor stub
	}

	@GetMapping("/transaction/refresh/{address}")
	public String refresh(@PathVariable String address) {
		this.syncTokenTransactionHistory(address);
		return "ok";
	}

	@GetMapping("/transaction/{address}")
	public Page<TransactionHistory> transaction(@PathVariable String address, @PageableDefault(sort = { "block_number" }) Pageable pageable) {
		Page<TransactionHistory> transactionHistory;
		int pageNumber = pageable.getPageNumber();
		System.out.println(pageNumber);
		this.syncTransactionHistory(address);
		transactionHistory = transactionHistoryRepository.findByAddressAndContractAddressIsNull(address, pageable);

		return transactionHistory;
	}

	@GetMapping("/transaction/{address}/{symbol}")
	public Page<TransactionHistory> transactionToken(@PathVariable String address, @PathVariable String symbol, @PageableDefault(sort = { "block_number" }) Pageable pageable) {
		Page<TransactionHistory> transactionHistory;
		this.syncTokenTransactionHistory(address);
		transactionHistory = transactionHistoryRepository.findTokenByAddressAndContactAddress(address, symbol, pageable);

		return transactionHistory;
	}

	private void syncTransactionHistory(String address) {
		TransactionPostion transactionPostion = transactionPostionRepository.findOneByAddress(address);

		System.out.println("POSTION: " + transactionPostion);

		String startblock;
		String endblock = (this.getBlockNumber());
		if (transactionPostion == null) {
			transactionPostion = new TransactionPostion();
			transactionPostion.setAddress(address);
			transactionPostion.setEthPostion(0);
			transactionPostion.setTokenPostion(0);

			startblock = "0";
		} else {
			startblock = String.valueOf(transactionPostion.getEthPostion() + 1);
		}

		TransactionResponse transactionsResponse = this.getTransactionsHistory(startblock, endblock, address);
		if (transactionsResponse.getStatus().equals("1")) {
			List<Transaction> transactions = transactionsResponse.getResult();
			for (Transaction transaction : transactions) {

				if (transaction.getContractAddress().equals("") && transaction.getInput().equals("0x")) {
					TransactionHistory transactionHistory = new TransactionHistory();
					transactionHistory.setBlockNumber(transaction.getBlockNumber());
					transactionHistory.setHash(transaction.getHash());
					transactionHistory.setSymbol("ETH");
					transactionHistory.setFrom(transaction.getFrom());
					transactionHistory.setTo(transaction.getTo());
					transactionHistory.setGas(transaction.getGas());
					transactionHistory.setGasPrice(transaction.getGasPrice());
					transactionHistory.setGasUsed(transaction.getGasUsed());
					transactionHistory.setIsError(transaction.getIsError());
					transactionHistory.setTimeStamp(transaction.getTimeStamp());
					transactionHistory.setValue(transaction.getValue());
					transactionHistoryRepository.save(transactionHistory);
				}
			}

			transactionPostion.setEthPostion(Integer.valueOf(endblock));
			transactionPostionRepository.save(transactionPostion);
		}

	}

	private void syncTokenTransactionHistory(String address) {
		TransactionPostion transactionPostion = transactionPostionRepository.findOneByAddress(address);

		System.out.println("POSTION: " + transactionPostion);

		String startblock;
		String endblock = (this.getBlockNumber());
		if (transactionPostion == null) {
			transactionPostion = new TransactionPostion();
			transactionPostion.setAddress(address);
			transactionPostion.setEthPostion(0);
			transactionPostion.setTokenPostion(0);
			startblock = "0";
		} else {
			startblock = String.valueOf(transactionPostion.getTokenPostion() + 1);
		}

		transactionPostion.setAddress(address);

		TokenTransactionResponse tokenTransactionResponse = this.getTokenTransactionsHistory(startblock, endblock, address);
		if (tokenTransactionResponse.getStatus().equals("1")) {
			List<TokenTransaction> tokenTransactions = tokenTransactionResponse.getResult();
			for (TokenTransaction token : tokenTransactions) {
				TransactionHistory transactionHistory = new TransactionHistory();
				transactionHistory.setBlockNumber(token.getBlockNumber());
				transactionHistory.setHash(token.getHash());
				transactionHistory.setSymbol(token.getTokenSymbol());
				transactionHistory.setFrom(token.getFrom());
				transactionHistory.setTo(token.getTo());
				transactionHistory.setGas(token.getGas());
				transactionHistory.setGasPrice(token.getGasPrice());
				transactionHistory.setGasUsed(token.getGasUsed());
				transactionHistory.setIsError("");
				transactionHistory.setTimeStamp(token.getTimeStamp());
				transactionHistory.setValue(token.getValue());
				transactionHistory.setContractAddress(token.getContractAddress());
				transactionHistory.setDecimals(Integer.valueOf(token.getTokenDecimal()));
				transactionHistoryRepository.save(transactionHistory);

				ethereumWallet.addToken(address, token.getContractAddress());
			}
			transactionPostion.setTokenPostion(Integer.valueOf(endblock));
			transactionPostionRepository.save(transactionPostion);
		}

	}

	private TransactionResponse getTransactionsHistory(String startblock, String endblock, String address) {
		final String url = ethereumDeveloperApis + "/api?module={module}&action={action}&address={address}&startblock={startblock}&endblock={endblock}&sort={sort}&apikey={apikey}";
		Map<String, String> params = new HashMap<String, String>();
		params.put("module", "account");
		params.put("action", "txlist");
		params.put("address", address);
		params.put("startblock", startblock);
		params.put("endblock", endblock);
		params.put("sort", "asc");
		params.put("apikey", "RT5JW37AKEZVSW3C91Z86IGI2FF7JDPF1N");
		RestTemplate restTemplate = new RestTemplate();
		TransactionResponse result = restTemplate.getForObject(url, TransactionResponse.class, params);
		logger.info(params.toString());
		logger.info(result.toString());
		return result;
	}

	private String getBlockNumber() {
		final String url = ethereumDeveloperApis + "/api?module={module}&action={action}&apikey={apikey}";
		Map<String, String> params = new HashMap<String, String>();
		params.put("module", "proxy");
		params.put("action", "eth_blockNumber");
		params.put("apikey", "RT5JW37AKEZVSW3C91Z86IGI2FF7JDPF1N");
		RestTemplate restTemplate = new RestTemplate();
		JsonRpc result = restTemplate.getForObject(url, JsonRpc.class, params);

		return Integer.valueOf(result.getResult().substring(2), 16).toString();
	}

	private TokenTransactionResponse getTokenTransactionsHistory(String startblock, String endblock, String address) {

		final String url = ethereumDeveloperApis + "/api?module={module}&action={action}&address={address}&startblock={startblock}&endblock={endblock}&sort={sort}&apikey={apikey}";
		Map<String, String> params = new HashMap<String, String>();
		params.put("module", "account");
		params.put("action", "tokentx");
		params.put("address", address);
		params.put("startblock", startblock);
		params.put("endblock", endblock);
		params.put("sort", "asc");
		params.put("apikey", "RT5JW37AKEZVSW3C91Z86IGI2FF7JDPF1N");
		RestTemplate restTemplate = new RestTemplate();
		TokenTransactionResponse result = restTemplate.getForObject(url, TokenTransactionResponse.class, params);
		logger.info(params.toString());
		logger.info(result.toString());
		return result;
	}
}
