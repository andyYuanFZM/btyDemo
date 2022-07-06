package com.chain33.cn.mintByManager;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.alibaba.fastjson.JSONObject;
import com.chain33.cn.CommonUtil;

import cn.chain33.javasdk.client.RpcClient;
import cn.chain33.javasdk.model.AccountInfo;
import cn.chain33.javasdk.model.decode.DecodeRawTransaction;
import cn.chain33.javasdk.model.rpcresult.QueryTransactionResult;
import cn.chain33.javasdk.utils.ByteUtil;
import cn.chain33.javasdk.utils.EvmUtil;
import cn.chain33.javasdk.utils.HexUtil;
import cn.chain33.javasdk.utils.TransactionUtil;

/**
 * NFT ERC1155 发行和转让
 *
 */
public class ERC1155Test {

	// TODO:需要设置参数 平行链所在服务器IP地址
	String ip = "172.22.16.179";
	// 平行链服务端口
	int port = 8901;
	RpcClient client = new RpcClient(ip, port);
	
    // 平行链名称，固定格式user.p.xxxx.样例中使用的名称叫mbaas， 根据自己平行链名称变化。  这个名称一定要和平行链配置文件中的名称完全一致。
	String paraName = "user.p.mbaas.";

	// TODO:需要设置参数： 合约部署人（管理员）地址和私钥,地址下需要有燃料来缴纳手续费
	// 生成方式参考下面testCreateAccount方法，私钥和地址一一对应
	String managerAddress = "14nh6p7CUNtLXAHEiVkSd5mLUWynzafHBx";
	String managerPrivateKey = "7dfe80684f7007b2829a28c85be681304f7f4cf6081303dbace925826e2891d1";
//	String managerAddress = "替换成自己的地址，用下面createAccount方法生成";
//	String managerPrivateKey = "替换成自己的私钥，用下面createAccount方法生成,注意私钥千万不能泄漏";
    
    // TODO:需要设置参数： 用户手续费代扣地址和私钥,地址下需要有一定燃料来缴纳手续费
	// 生成方式参考下面testCreateAccount方法，私钥和地址一一对应
	String withholdAddress = "17RH6oiMbUjat3AAyQeifNiACPFefvz3Au";
    String withholdPrivateKey = "56d1272fcf806c3c5105f3536e39c8b33f88cb8971011dfe5886159201884763";
//	String withholdAddress = "替换成自己的地址，用下面createAccount方法生成";
//    String withholdPrivateKey = "替换成自己的私钥，用下面createAccount方法生成,注意私钥千万不能泄漏";
    
    // 用户A地址和私钥
	String useraAddress;
    String useraPrivateKey;
    
    // 用户B地址和私钥
	String userbAddress;
    String userbPrivateKey;
    
    /**
     * ERC1155合约部署，调用测试
     * @throws Exception 
     */
    @Test
    public void testERC1155() throws Exception {
    	
    	
    	// =======> step1： 为用户A和B生成私钥和地址
    	AccountInfo infoA = CommonUtil.createAccount();
    	useraAddress = infoA.getAddress();
    	useraPrivateKey = infoA.getPrivateKey();
    	
    	AccountInfo infoB = CommonUtil.createAccount();
    	userbAddress = infoB.getAddress();
    	userbPrivateKey = infoB.getPrivateKey();
    	
    	// =======>  step2: 通过管理员部署合约，部署好之后，合约就运行区块链内存中，后续可以直接调用，不用每次都调用部署合约这一步操作（除非业务上有需要）
        // 部署合约, 参数： 平行链合约名， 签名地址，签名私钥
        String hash = deployContract(paraName, managerAddress, managerPrivateKey);
        
        // 计算上一步部署到链上的合约地址
        String contractAddress = TransactionUtil.convertExectoAddr(managerAddress + hash.substring(2));
        System.out.println("部署好的合约地址 = " + contractAddress);
        
        // =======>  step3: 调用合约发行NFT,假设为2件游戏道具各生成100个NFT资产, id从10000开始
        // ERC1155支持同一个TOKENID发行多个数量（同一幅画多个副本）， 如果想实现一画一token，传入合约的数组length设置为1
        int lenght = 100;
        // tokenId数组
        int[] ids = new int[lenght];
        // 同一个tokenid发行多少份
        int[] amounts = new int[lenght];
        // 每一个tokenid对应的URI信息（一般对应存放图片的描述信息，图片内容的一个url）
        String[] uris = new String[lenght];
        for (int i = 0; i < lenght; i++) {
        	ids[i] = 10000 + i;
        	amounts[i] = 100;
        	// 例子为了简化处理，让所有ID都固定一个地址，
        	uris[i] = "{\"图片描述\":\"由xxx创作\";\"创作时间\":\"2022/12/25\";\"图片存放路径\":\"http://www.baidu.com\"}";
        }
        // 构造合约调用, mint对应solidity合约里的方法名， useraAddress, ids, amounts这三项对应合约里的参数。  将NFT发行在用户A地址下
        byte[] initNFT = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "mint", useraAddress, ids, amounts, uris);
        
        hash = callContract(initNFT, contractAddress, managerAddress, managerPrivateKey, paraName);
        
        // =======>  查询用户A地址下的余额
        byte[] packAbiGet = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "balanceOf", useraAddress, ids[0]);
        queryContract(packAbiGet, contractAddress, "转账前用户A,NFTID=" + ids[0] + "余额");
        
        packAbiGet = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "balanceOf", useraAddress, ids[1]);
        queryContract(packAbiGet, contractAddress, "转账前用户A,NFTID=" + ids[1] + "余额");
        
        // =======>  从A地址向B地址转账,使用代扣交易
        // 代扣交易需要对平行链合约地址做一个处理
        String execer = paraName + "evm";
        // 平行链合约地址计算(平行链title前缀+合约名称)
        String paracontractAddress = client.convertExectoAddr(execer);
        // 用户A将第1个NFT中的50个转给用户B
    	byte[] transfer = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "transferArtNFT", userbAddress, ids[0], 50);
    	
    	// transfer NFT的GAS费
        String evmCode = EvmUtil.getCallEvmEncode(transfer, "transfer", 0, contractAddress, paraName);
        long gas = client.queryEVMGas("evm", evmCode, useraAddress);
        System.out.println("调用transfer方法的Gas fee:" + gas);
    	
    	// 构造转账交易体，先用用户A对此笔交易签名，
    	String txEncode = EvmUtil.callEvmContractWithholdByGas(transfer,"", 0, execer, useraPrivateKey, contractAddress, gas);
    	    	
    	// 再调用代扣交易方法，用代扣私钥对交易组做签名
    	createNobalance(txEncode, paracontractAddress, useraPrivateKey, withholdPrivateKey);
        
        // =======>  查询用户A地址下的余额
        packAbiGet = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "balanceOf", useraAddress, ids[0]);
        queryContract(packAbiGet, contractAddress, "转账后用户A,NFTID=" + ids[0] + "余额");
        
        // =======>  查询用户B地址下的余额
        packAbiGet = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "balanceOf", userbAddress, ids[0]);
        queryContract(packAbiGet, contractAddress, "转账后用户B,NFTID=" + ids[0] + "余额");
        
        // =======>  查询指定tokenid的uri信息
        packAbiGet = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "uri", ids[0]);
        queryContractString(packAbiGet, contractAddress, "NFTID=" + ids[0] + "的URI信息");
        
        // =======>  查询指定tokenid的uri信息
        packAbiGet = EvmUtil.encodeParameter(CommonUtil.abi_Manager_1155, "uri", ids[1]);
        queryContractString(packAbiGet, contractAddress, "NFTID=" + ids[1] + "的URI信息");
        
    }
        
    /**
     * Step2:部署合约
     * @throws Exception
     */
    private String deployContract(String execer, String address, String privateKey) throws Exception {

        // 部署合约
        String txEncode;
        String txhash = "";
        QueryTransactionResult txResult = new QueryTransactionResult();
        
        byte[] code = ByteUtil.merge(HexUtil.fromHexString(CommonUtil.byteCode_Manager_1155), CommonUtil.abi_Manager_1155.getBytes());
        
    	// 估算部署合约GAS费
        String evmCode = EvmUtil.getCreateEvmEncode(code, "", "deploy ERC1155 contract", execer);
        long gas = client.queryEVMGas("evm", evmCode, address);
        System.out.println("Gas fee is:" + gas);
        
        // 通过合约code, 管理员私钥，平行链名称+evm,手续费等参数构造部署合约交易，并签名
        txEncode = EvmUtil.createEvmContract(code, "", "evm-sdk-test", privateKey, execer, gas);
        // 将构造并签好名的交易通过rpc接口发送到平行链上
        txhash = client.submitTransaction(txEncode);
        System.out.println("部署合约交易hash = " + txhash);
        
        // BTY平均3-5秒一个区块确认， 需要延时去查结果
        Thread.sleep(10000);
		for (int tick = 0; tick < 30; tick++){
			txResult = client.queryTransaction(txhash);
			if(txResult == null) {
				Thread.sleep(3000);
				continue;
			}			
			break;
		}
		
		if ("ExecOk".equals(txResult.getReceipt().getTyname())) {
			System.out.println("合约部署成功");

		} else {
			System.out.println("合约部署失败,请通过区块链浏览器或命令行查看失败原因" );
		}
		
		return txhash;
    }
    
    /**
     * Step3: 调用合约
     * @param contractAddr
     * @param address
     * @param privateKey
     * @throws IOException 
     * @throws InterruptedException 
     */
    private String callContract(byte[] code, String contractAddr, String address, String privateKey, String execer) throws Exception {
    	
        // 调用合约
        String txEncode;
        String txhash = "";
        QueryTransactionResult txResult = new QueryTransactionResult();
        
        // 估算合约执行GAS费
        String evmCode = EvmUtil.getCallEvmEncode(code, "", 0, contractAddr, execer);
        long gas = client.queryEVMGas("evm", evmCode, address);
        System.out.println("调用合约方法的Gas fee:" + gas);
    	
        txEncode = EvmUtil.callEvmContract(code,"", 0, contractAddr, privateKey, execer, gas);
    	
        txhash = client.submitTransaction(txEncode);
        System.out.println("调用合约hash = " + txhash);
        
        // 平均3-5秒一个区块确认， 需要延时去查结果
        Thread.sleep(10000);
		for (int tick = 0; tick < 30; tick++){
			txResult = client.queryTransaction(txhash);
			if(txResult == null) {
				Thread.sleep(3000);
				continue;
			}			
			break;
		}
		
		if ("ExecOk".equals(txResult.getReceipt().getTyname())) {
			System.out.println("合约调用成功");
			
		} else {
			System.out.println("合约调用失败，请通过区块链浏览器或命令行查看失败原因");
		}
		
		return txhash;
    	
    }
    
    /**
     * 查询方法
     * @param queryAbi
     * @param contractAddress
     * @throws Exception 
     */
    private void queryContract(byte[] queryAbi, String contractAddress, String title) throws Exception {
        // 查询用户A和用户B地址下的资产余额
        JSONObject query = client.callEVMAbi(contractAddress, HexUtil.toHexString(queryAbi));
        JSONObject output = query.getJSONObject("result");
        String rawData = output.getString("rawData");
        System.out.println(title + ": " + HexUtil.hexStringToAlgorism(HexUtil.removeHexHeader(rawData)));
    }
    
    /**
     * 查询方法
     * @param queryAbi
     * @param contractAddress
     * @throws Exception 
     */
    private void queryContractString(byte[] queryAbi, String contractAddress, String title) throws Exception {
        // 查询用户A和用户B地址下的资产余额
        JSONObject query = client.callEVMAbi(contractAddress, HexUtil.toHexString(queryAbi));
        JSONObject output = query.getJSONObject("result");
        String rawData = output.getString("rawData");
        System.out.println(title + ": " + HexUtil.hexStringToString(HexUtil.removeHexHeader(rawData)).replaceAll("\u0000",""));
    }
          
    
    /**
     * 构建代扣手续费交易
     * 
     * @param txEncode
     * @param contranctAddress
     * @return
     * @throws InterruptedException
     * @throws IOException 
     */
    private String createNobalance(String txEncode, String contranctAddress, String userPrivatekey, String withHoldPrivateKey) throws Exception {
        String createNoBalanceTx = client.createNoBalanceTx(txEncode, "");
	    // 解析交易
	    List<DecodeRawTransaction> decodeRawTransactions = client.decodeRawTransaction(createNoBalanceTx);
	    
	    String hexString = TransactionUtil.signDecodeTx(decodeRawTransactions, contranctAddress, userPrivatekey, withHoldPrivateKey);
	    
	    String submitTransaction = client.submitTransaction(hexString);
	    System.out.println("代扣hash= " + submitTransaction);
	    
	    String nextString = null;
        QueryTransactionResult txResult = new QueryTransactionResult();

		Thread.sleep(10000);
		for (int tick = 0; tick < 30; tick++){
			QueryTransactionResult result = client.queryTransaction(submitTransaction);
			if(result == null) {
				Thread.sleep(3000);
				continue;
			}

			System.out.println("next:" + result.getTx().getNext());
			QueryTransactionResult nextResult = client.queryTransaction(result.getTx().getNext());
			System.out.println("ty:" + nextResult.getReceipt().getTyname());
			nextString = result.getTx().getNext();
			break;
		}
		
		
		txResult = client.queryTransaction(nextString);
		if ("ExecOk".equals(txResult.getReceipt().getTyname())) {
			System.out.println("合约调用成功");
			
		} else {
			System.out.println("合约调用失败，请通过区块链浏览器或命令行查看失败原因");
		}
		return nextString;
    }
    
}
