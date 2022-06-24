# BTY+平行链EVM合约使用教程

## 文档目录
	- [文档修改记录](#文档修改记录)
	- [文档阅读说明](#文档阅读说明)
	- [术语介绍](#术语介绍)
	- [背景介绍](#背景介绍)
	- [主链和平行链环境部署](#主链和平行链环境部署)
	- [NFT合约概述](#NFT合约概述 )
	- [通过SDK实现合约部署调用](#通过SDK实现合约部署调用)
	- [应用和BTY平行链对接注意事项](#应用和BTY平行链对接注意事项)

### 文档修改记
| 版本号 | 版本描述                              | 修改日期   | 备注 |
| ------ | ------------------------------------- | ---------- | ---- |
| V1.0   | 1. 主链+平行链部署<br>2. NFT合约简单概述<br>3.通过 JAVA-SDK 在平行链上发行 NFT<br>4. 对接注意事项| 2022/06/03 |

### 文档阅读说明
- 术语介绍：简单了解  
- 区块链环境部署：已经具备环境情况下，可忽略；***还未部署的重点了解。  ***
- NFT合约概述： 了解下概念，文档中有NFT合约的样例，如果样例太简单满足不了业务需求，需要自己开发新合约的，可以重点了解  
- 通过SDK实现合约部署调用：***重点了解***
- 应用和BTY平行链对接注意事项: ***重点了解，是项目中踩过的坑，了解过后可少走弯路。***

### 术语介绍 
| 序号 | 术语 缩写                              | 解释   |
| ------- | -------------------------------------- | --------------------- |
| 1   | BTY主链| BTY主链采用SPOS共识机制，节点可随意加入和退出集群|
| 2   | 平行链| 平行链依附于主链，平行链之间通过名称来区分，平行链与平行链之间数据相互隔离， 平行链与主链之间通过grpc通信。|
| 3   | EVM| 以太坊虚拟机的缩写，目前EVM算是区块链中最大的生态，很多链都支持EVM的能力，森田平行链也完全兼容EVM,通过EVM可以动态的部署智能合约进行计算|
| 4   | ERC721| 运行在EVM中，服务于非同质化代币（NFT）, 每个Token都是不一样的，都有自己的唯一性和独特价值,不可分割，可追踪。|
| 5   | ERC1155| 运行在EVM中，也是服务于非同质化代币(NFT),相比于ERC721它同时还支持在一个合约中存储多个数字资产，支持一次性批量发行多个不同类型的的数字资产，支持在一次转账过程中转多个不同类型的数字资产。|
| 6   | 交易组| 把两笔及以上的交易放在一个组里一次性发送。|
| 7   | 代扣手续费| 将代扣交易和正常用户的交易打包进一个交易组中，代扣交易使用代扣地址签名，用于主链上手续费扣除。|
| 8   | SDK| 封装了同区块链交互的接口和区块链通用方法（包括：公私钥生成，签名，交易构造等）, 支持java-sdk, go-sdk, web3.js等 |

### 背景介绍
BTY公链和平行链都是基于Chain33区块链开发框架开发出来的，主链和平行链的的区别就是加载了不同的共识机制插件。      
- Ticket共识插件：BTY主链共识插件，采用安全的POS机制，平均3秒左右一个区块，适用于大规模多共识节点的部署，共识节点可以方便的加入和退出。   
- PARA共识插件：平行链共识插件，平行链不是独立存在的，它依附于主链（上述的5种都是主链），利用主链的共识算法来保证其安全性，同时平行链实现交易执行分片，主链下可以挂很多不同名称的平行链，每条平行链只负责自己独立的业务。 平行链条数的增加不会影响主链的性能，也不会影响其它平行链的性能。 比如EVM合约运行在平行链上， 而主链上只对这些交易的原始信息做共识和存证，所以主链只做存证而不用做具体的计算性能就可以很高。   

### 主链和平行链环境部署
#### 说明  
主链采用Ticket共识机制, Ticket共识是一种安全的POS共识机制（SPOS）, 用户通过购买票（Ticket）获得投票权, 投票权重的多少跟用户拥有的票数量和持有时间有关。  
主链区块链浏览器： [区块链浏览器地址](https://mainnet.bityuan.com/home)   
主链+平行链交易流程：  
- 交易在链下完成构造和签名,交易构造时需要在交易体中带上对应平行链的名称。   
- 签好名的交易通过平行链的jsonrpc接口发往平行链节点。   
- 平行链通过它和主链之间的grpc连接,将交易转发到主链节点,由主链打包区块共识后存入主链账本。   
- 主链区块生成后,平行链实时拉取新产生的区块,过滤出属于本平行链的交易（根据平行链名称）, 送入虚拟机执行后并写入平行链账本。  
下面介绍主链节点和平行链节点的部署，智能合约部署和调用方法。  
+ 注： 支持在同一台服务器上同时部署BTY主链节点和BTY平行链节点（只要保证两者的jsonrpc和grpc端口不冲突即可）   
	
#### 主链节点同步：  
目前主链已经有100多G的区块数据，同步时间会比较长（SSD硬盘情况下超过4天），建议提前规划时间。  
- 准备一台最小配置为4核8G的linux服器（建议配置8核16G, ubuntu或Centos都可），硬盘>300G （同步速度SSD硬盘效率要远远高于机械盘，根据自己情况选择硬盘类型）  

- 从 https://github.com/bityuan/bityuan/releases 下载最新版本的release运行(比如以当前最新版本6.8.0来说明)        
```  
# 下载
wget https://github.com/bityuan/bityuan/releases/download/v6.8.0/bityuan-linux-amd64.tar.gz
# 新建目录
mkdir bityuan
# 解压
tar -zxvf bityuan-linux-amd64.tar.gz -C bityuan
```  

- 目录下包含以下几个文件  
```  
bityuan-linux-amd64                -- BTY节点程序
bityuan-cli-linux-amd64            -- BTY节点命令行工具
bityuan.toml                       -- bityuan配置文件（带数据分片功能，占空间小），后面启动时用这个配置
bityuan-fullnode.toml              -- bityuan配置文件（全节点模式，占空间大）
```  

- 修改配置文件 
```  
[blockchain]
 # 默认是false, 需要改成true， 此参数含义是主链给平行链同步区块sequence信息，如果不开启，平行链无法同步主链的区块  
isRecordBlockSequence=true  
[rpc]  
 # 数据上链jsonrpc端口, 默认只限制localhost访问，去掉localhost只保留":8801"代表允许远端访问 
jrpcBindAddr="localhost:8801"
 # 数据上链grpc端口, 默认只限制localhost访问，去掉localhost只保留":8802"代表允许远端访问 
grpcBindAddr="localhost:8802"  
 # 白名单IP,限制哪些IP有访问权限： 多个ip之间以逗号分隔,127.0.0.1代表只能本地访问, * 代表不作任何限制  
whitelist=["127.0.0.1"] 
```  

- 启动主链程序
```  
nohup ./bityuan-linux-amd64 -f bityuan.toml >> bty.out&
```  
- 检查主链的同步状态(进程启动后，等待一会后执行) 
```  
#  主要看返回信息中自己节点的height信息， 和主链最大高度一致代表同步成功。  这一过程时间比较长，按目前2000万左右的区块高度， SSD硬盘同步需要三天左右时间， 普通机械硬盘耗时可能翻倍
 ./bityuan-cli-linux-amd64 net peer info  
```   

- 创建钱包，接收空投 (这一步可以在主链节点还没有同步完情况下执行)   
每一笔交易上链，都需要支付BTY作为手续费， 前期测试交易量比较少的情况下，可以用空投的BTY来充当手续费
```  
# 生成助记词，以下命令执行完返回的一串英文字符串就是助记词  
./bityuan-cli-linux-amd64 seed generate -l 0 
# 保存助记词
./bityuan-cli-linux-amd64 seed save -s "上一步返回的助记词" -p 钱包密码（必须小写字母+数据的组合，比如abcd1234这种）
# 解锁钱包  
./bityuan-cli-linux-amd64 wallet unlock -p 上一步设置的钱包密码
# 查看钱包里资产， 其中标签名为dht node award和airdropaddr两个地址下，每天都会收到BTY空投， 可以覆盖测试用的手续费
./bityuan-cli-linux-amd64 account list
```  

- 转移空投地址下的BTY到另外的地址(只有在主链节点同步完后，才能看到空投资产，所以这一步要在同步完后再执行,同步过程中执行会报一个：ErrNotSync的错)   
```  
# 查看空投地址的私钥
./bityuan-cli-linux-amd64 account dump_key -a 空投地址
# 将BTY从空投地址转到另外地址下（分三步，构造交易，签名，发送）
./bityuan-cli-linux-amd64  coins transfer -a 100 -n test -t  用户另外的地址
./bityuan-cli-linux-amd64  wallet sign -k 空投地址的私钥 -d 上一步返回的数据
./bityuan-cli-linux-amd64  wallet send -d 上一步返回的数据
# 查看转账交易的执行结果
通过区块链浏览器，输入地址或上一步返回的转账交易hash值，去浏览器上查询   
浏览器地址： https://mainnet.bityuan.com/home
```   
 
#### 平行链节点部署 
正常需要完成主链同步才可以部署平行链。 可以临时使用官方的主链连接做开发测试, 等自己主链节点同步完后,再改成自己的连接,具体见下文配置说明 。  
- 下载BTY平行链，解压压缩包，并进入目录  
```  
wget https://bty33.oss-cn-shanghai.aliyuncs.com/chain33Dev/parachain/linux/chain33_para_linux_0670237.tar.gz  
tar -zxvf chain33_para_linux_0670237.tar.gz  
cd chain33_para_linux_0670237  
```  

- 目录下包含以下三个文件  
```  
chain33                -- chain33节点程序
chain33-cli            -- chain33节点命令行工具
chain33.para.toml      -- chain33平行链配置文件
```  

- 修改配置文件（chain33.para.toml），修改以下几个配置项：  
```  
 #平行链名称，用来唯一标识一条平行链，  可将mbaas修改成自己想要的名称（只支持英文字符），最后一个 . 号不能省略
Title="user.p.mbaas."
 #主链的grpc地址，可以临时使用官方的grpc连接：ParaRemoteGrpcClient="jiedian2.bityuan.com,cloud.bityuan.com"
 #上述连接只用于开发测试，商用的话，需要将指向自己部署好的主链IP:8802，这样通信更流畅
ParaRemoteGrpcClient="localhost:8802"
 #指示从主链哪个高度开始同步，比如目前主链高度是19391000，建议配置是提前1000个区块（19391000-1000=19390000）  
 #主链高度可以通过区块链浏览器来查询： https://mainnet.bityuan.com/home  
startHeight=1  ==> 改成 startHeight=19390000
```  

- 启动平行链
```  
nohup ./chain33 -f chain33.para.toml >> para.out&  
```  

- 检查平行链和主链的同步状态(进程启动后，等待一会后再执行)  
```
 #返回true代表同步完成
./chain33-cli --rpc_laddr="http://localhost:8901" para is_sync
 # 当前平行链最大区块高度
./chain33-cli --rpc_laddr="http://localhost:8901" block last_header
```  
备注：如果主链或平行链部署过程中遇到问题，可联系官方客服确认。

### NFT合约概述
NFT合约运行在平行链的EVM虚拟机中, EVM虚拟机运行solidity语言编写和编译的智能合约。   
Solidity语言更多信息, 请参阅  [[Solidity中文官方文档]](https://learnblockchain.cn/docs/solidity/)  
下文[NFT合约开发编译]链接介绍ERC1155和ERC721两类合约最简单的使用。  
两种合约的基本介绍， 合约的编写和编译，以及和链的交互流程等 [[NFT合约开发编译]](https://github.com/andyYuanFZM/btyDemo/blob/master/NFT合约开发编译.md)   

### 通过SDK实现合约部署调用     
#### JAVA-SDK
适用于应用平台使用JAVA开发的情况,提供SDK对应的jar包，SDK里包含了公私钥生成,合约部署方法,合约调用方法,交易签名,交易查询,区块链信息查询等方法。   
JAVA-SDK环境开发环境部署参考链接： [[JAVA-SDK]](https://github.com/andyYuanFZM/btyDemo/blob/master/JAVA-SDK开发环境.md)  

1. 子目录说明
mintByManager目录下的NFT合约只支持管理员来发行NFT， 适用于平台对于NFT发行有严格限制的业务场景。（**最常用，一般情况下都是用这种方式**）  
mintByUser目录下的NFT合约不限制只有管理员才能发行，任何用户都可以调用mint方法发行NFT， 适用于平台任意作者都可以发行NFT的业务场景。   
deployByUser此目录下的用例支持任意用户都可以部署NFT合约，适用于平台支持每一个艺术家都可以部署自己的智能合约（这种场景较少）。  

**目前使用最多的是mintByManager目录下的ERC1155合约, 如果不确定要用什么，建议用这个**  

2. 运行JAVA Demo程序  
- 调用 [[BlockChainTest.java]](https://github.com/andyYuanFZM/btyDemo/blob/master/src/test/java/com/chain33/cn/BlockChainTest.java)  中的createAccount方法，生成地址和私钥  
- 修改对应子目录下的ERC1155Test或ERC721Test文件，将上一步生成的内容，分别填充到以下几个参数中，注意私钥即资产，要隐私存放，而地址是可以公开的  
```  
// 管理员地址和私钥
String managerAddress = "";
String managerPrivateKey = "";
    
// 代扣地址和私钥,用于所有用户的手续费代扣,避免用户接触燃料费这一底层概念
String withholdAddress = "";
String withholdPrivateKey = "";
```  
- 给上述两个地址下充值测试用的燃料  
- 修改ERC1155Test或ERC721Test两个文件中以下两个参数  
```  
// 改成自己平行链所在服务器IP地址
String ip = "";
// 改成自己平行链服务端口，对应的是配置文件里的jrpcBindAddr配置项，默认的是8901。 注意：如果远程访问，防火墙要放行此端口
int port = 8901;
```   
- 修改平行链名称  
```  
// 改成和实际部署的平行链一致，注意最后的.号不能省去
String paraName = "user.p.mbaas.";
```   
- 运行测试程序  

#### GO-SDK  
适用于应用平台使用Golang开发的情况,SDK里包含了公私钥生成,合约部署方法,合约调用方法,交易签名,交易查询,区块链信息查询等方法。   
GO-SDK的使用参考链接： [[GO-SDK]](https://github.com/33cn/chain33-sdk-go)   

## 应用和BTY平行链对接注意事项   
由于BTY主链涉及燃料费,同时森田主链平均每3秒一个确认的特性,可能会存在交易的失败,主要有以下两大类情况：  
1. 交易上链了，但交易执行失败（有返回交易hash）：   这类交易通过了mempool（交易缓存池）的合法性检查，但是在合约执行过程中失败了（ 比如转移了错误数量的NFT）。
2. 交易没有上链（没有返回交易hash，rpc接口直接返回出错信息）： 这类交易在mempool的合法性检查中没有通过，包括以下以类错误：  
	- 签名错误（ErrSign）： 签名校验不通过，一般不会遇到，除非人为去改交易内容。  -- 不常见   
	- 交易重复（ErrDupTx）：mempool中发现重复交易，一般不会遇到，除非人为发送重复交易（所谓重复是hash完全一模一样的两笔交易，而不是指业务上数据相同）。 -- 不常见   
	- 手续费不足： 代扣地址下手续费不足会导致交易无法上链，需要保证代扣地址下GAS费充足。  -- 有可能会遇到  
	- 手续费太低（ErrTxFeeTooLow）： 交易设置的手续费比链上要求的手续费低，常见于部署EVM合约或批量发行大量NFT的场合， 需要通过queryEVMGas预估计出一个GAS费，然后在这个基础上再加上0.001作为手续费，这样能保证交易不会上链失败。 -- 有可能遇到，参考用例中手续费设置方式 
	- 交易账户在mempool中存在过多交易（ErrManyTx）： BTY为防止来自于同一个地址的频繁交易，限制每个账户在mempool中的最大交易数量不能超过100， 所以当交易频率很高时，mempool中代扣手续费的交易（都是来自同一个代扣地址）可能会超过100的， 而100笔以后的交易会被丢弃（rpc返回errmanytx的错）从而导致关联的交易也被丢弃。   -- 有可能会遇到   

举例说明应用层和区块链整合后的一般处理方案：  
为保证数据的一致性，需要判断交易在区块链上确实成功（拿到交易hash，且实际交易的执行结果是：ExecOk）, 业务上才能判定为成功。    且BTY支持以代扣的方式来扣除用户交易的燃料费， 代扣包含两笔交易：第一笔是代扣的交易（最简单的存证）， 第二笔是实际用户的交易。  交易上链后，区块链返回的是第一笔交易的hash值， 而这笔交易hash不能用于判断交易是否执行成功，需要拿这个hash值查到对应的第二笔交易才能判断。  具体见下面处理流程以及测试用例：  

场景：NFT平台上新品，大量用户抢购。  
处理流程：  
1. 用户完成支付，应用层触发NFT转账动作，并拿到区块链返回的交易hash(在采用代扣的情况下，这个hash对应的是代扣手续费交易，而非实际转NFT交易)  
2. 由于区块链是异步处理且有一定时延，这时NFT资产还没有真正转到用户地址下，但应用层可以预先将该笔订单标记为【待完成】。 这个状态不用给用户显示，只由应用层记录，对于用户而言，他只要一完成支付，就视为成功，不需要让他等待区块链的处理结果。  
3. 应用层把第1步中拿到的hash放到处理队列中，定时拿这个hash去查结果，如果查询结果返回空，hash继续留在队列中等待下一次查询， 如果结果不为空，从返回结果中拿到第二笔交易的hash,再根据这第二笔的交易hash去查询（这一步查询不用等待，拿到hash后可以马上查），从这次查询的返回结果拿到执行结果，如果是ExecOk代表执行成功，应用层再【待完成】的订单标记为【完成】，并从处理队列中删除第一笔交易的hash。 如果结果是ExecPack，代表执行失败，这个一般可能是业务上的bug(比如转了超过地址下数量的NFT资产，或是权限不够等等)，这种情况后续怎么处理由应用层根据实际情况来判断。    
4. 其它异常  
4.1 根据hash一直查不到有结果返回的可能性况： 1. 部署的平行链名称和上链交易中带的平行链名称不一致。  2.  平行链连接的主链节点高度落后于主网最大高度，或是平行连连接的那个主链节点离线。  
4.2 数据上链后，没有拿到交易hash，且rpc返回ErrTxFeeTooLow， 代表设置的交易手续费低于区块链所需的最低手续费。 需要在发交易前调用查询GAS费接口来估算出手续费（参考用例），再设置到交易中。  
4.3 数据上链后，没有拿到交易hash，且rpc返回ErrManyTx。  有大量交易上来时，应用层用队列缓存发送，比如每间隔10秒发送100笔交易，这样基本能解决ErrManyTx问题，如果再有个别交易还发生ErrManyTx，也需要把失败的再放回队列，后面重新发送。  
4.4 数据上链后，没有拿到交易hash，且rpc返回ErrTxMsgSizeTooBig， 代表交易体太大。 一般出现于ERC1155批量mint一批NFT时， 建议一次mint的token数量不要超过1000个， 数量比较大的，可以分多次来mint。  
4.5 定期检查代扣地址下的余额，保证手续费充足。  
