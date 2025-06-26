<div align="center">
<h1>
ModelCache Testing
</h1>
</div>

This repository is a testing and benchmarking repository for the [ModelCache](https://github.com/codefuse-ai/ModelCache) project\
It was created to benchmark performance improvements delievered by the [yuval-roth/ModelCache](https://github.com/yuval-roth/ModelCache) fork

This readme covers how to run the CLI and the test suite.

## Setting up the ModelCache environment

#### Before all changes:
1. Checkout the first commit: [Made it run](https://github.com/Yuval-Roth/ModelCache/commit/82ba867408949bf896d1317a4d17d6c4408b84da) (hash: 82ba867408949bf896d1317a4d17d6c4408b84da)
2. Delete the existing `model/text2vec-base-chinese` folder and clone [the embedding model's repository](https://huggingface.co/shibing624/text2vec-base-chinese/tree/main) into the `/model` folder
3. Delete the `data/mysql/db` and `data/milvus/db` folders if they exist and run `docker compose up -d` in the repository root (install docker first if you don't have it)
4. Set up a virtual environment for python **3.10** and activate it
5. Run `pip install -r requirements.txt` in the repository root
6. Run `python flask4modelcache.py` (make sure port 5000 is available)

#### After all changes:
1. Checkout the main branch
2. Delete the `data/mysql/db` and `data/milvus/db` folders if they exist and run `docker compose up -d` in the repository root (install docker first if you don't have it)
3. Set up a virtual environment for python **3.12** and activate it
4. Run `pip install -r requirements.txt` in the repository root
5. Adjust the amount of embedding workers to run in the `websocket4modelcache.py` file
```
@asynccontextmanager
async def lifespan(app: FastAPI):
    global cache
    cache, _ = await Cache.init(
        sql_storage="mysql",
        vector_storage="milvus",
        embedding_model=EmbeddingModel.HUGGINGFACE_ALL_MPNET_BASE_V2,
************************************************************************
        embedding_workers_num=8  <----------- ADJUST THE AMOUNT HERE
************************************************************************
    )
    yield

```
6. Run `python websocket4modelcache.py` (make sure port 5000 is available)

Setting up the environment for everything in between the first and last commit can be tricky and it's best to read the commit log to figure out what to change.

---

## Running this project
1. Checkout the main branch
2. Run `mvn clean package` in the repository root
3. Run `java -jar target\modelcache-testing-1.0.jar`

## Using this project
When running the project, you will be given a choice.
```
Pick an option:
1. Run CLI            <------- manual CLI for running basic hand-crafted queries
2. Run Test Suite     <------- automatic test suite
3. Exit
>>
```

### CLI
When picking the CLI you are given 3 choices for the target server
```
Welcome to ModelCache CLI
Select a target server:
1. flask
2. fastAPI
3. Websocket
```
After selecting a choice, you are free to use the CLI as you please
```
Select an option:
1. Insert to cache
2. Query cache
3. Clear cache
4. Exit
>>
```

### Test Suite
When picking the test suite, you are given 2 presets and an option for a custom test, which requires more configuration.\
The custom test is mostly for in-between commits and is not needed for testing before-all and after-all changes

```
Select test:
1. New system         <------- preset
2. Old system         <------- preset
3. Custom test
>>
```

Note: The `Worker count` parameter controls the amount of threads sending requests to the ModelCache system in parallel

#### Presets
When picking the `New system` preset you will have to select both the target server and the worker count
```
Select target server:
1. flask
2. fastAPI
3. Websocket
>> 3
Worker count:
>>
```
When picking the `Old system` preset you will have less target server choice and the worker count is locked at 1
```
Select target server:
1. flask
2. fastAPI
>> 
```

#### Custom test
When picking the `Custom test` option you will have to supply all the parameters.\
Note: not all parameters are compatible with each other.
```
Select test:
1. New system
2. Old system
3. Custom test
>> 3
Select target server:
1. flask
2. fastAPI
3. Websocket
>> 3
Enable bulk insert?
1. Yes
2. No
>> 1
Query prefix:
1. new prefix ('user: ')
2. old prefix ('user###')
>> 1
Worker count:
>> 8
```
\-\-\-\-\-\-\-\-\-\-\-\-\- </br>

After inserting all the required parameters, it will print the parameters to the screen and start running the test
```
Running test suite with the following configuration:
Target server  :  websocket
Bulk insert    :  true
Query prefix   :  'user: '
Workers count  :  8

Running test: insert questions1 => self-lookup questions1 ....
```

The test will continue printing the results until all the tests are done.\
The final result will look something like this:
```
Running test suite with the following configuration:
Target server  :  websocket
Bulk insert    :  true
Query prefix   :  'user: '
Workers count  :  16

Running test: insert questions1 => self-lookup questions1 .... Done in 29911ms
Running test: questions1 loaded => pair-lookup questions2 .... Done in 15600ms
Running test: insert questions2 => self-lookup questions2 .... Done in 29036ms
Running test: questions2 loaded => pair-lookup questions1 .... Done in 15723ms
-------------------------------------------------------

Test number 1
Test name: insert questions1 => self-lookup questions1

Statistics:

Insertion time: 13884 ms
Querying time: 16026 ms
Total time: 29911 ms
Hit ratio: 1666/1666 (100.0%)
Expected query hit ratio: 1666/1666 (100.0%)
Query throughput: 103.95608 queries/second
Mean query latency: 153.14465 ms
p95 query latency: 185.0 ms
p99 percentile query latency: 214.69995 ms
Mean memory usage: 58.722008%
p95 memory usage: 58.979794%
p99 memory usage: 58.986813%
Mean CPU usage: 79.1111%
p95 CPU usage: 99.069336%
p99 CPU usage: 100.0%


-------------------------------------------------------

Test number 2
Test name: questions1 loaded => pair-lookup questions2

Statistics:

Insertion time: 0 ms
Querying time: 15600 ms
Total time: 15600 ms
Hit ratio: 1183/1668 (70.92326%)
Expected query hit ratio: 1092/1183 (92.30769%)
Query throughput: 106.92307 queries/second
Mean query latency: 148.90408 ms
p95 query latency: 178.65002 ms
p99 percentile query latency: 194.0 ms
Mean memory usage: 58.924526%
p95 memory usage: 59.031788%
p99 memory usage: 59.03745%
Mean CPU usage: 73.88736%
p95 CPU usage: 79.65905%
p99 CPU usage: 79.819664%


-------------------------------------------------------

Test number 3
Test name: insert questions2 => self-lookup questions2

Statistics:

Insertion time: 13004 ms
Querying time: 16032 ms
Total time: 29036 ms
Hit ratio: 1668/1668 (100.0%)
Expected query hit ratio: 1668/1668 (100.0%)
Query throughput: 104.041916 queries/second
Mean query latency: 153.07314 ms
p95 query latency: 185.0 ms
p99 percentile query latency: 206.32996 ms
Mean memory usage: 59.62415%
p95 memory usage: 59.84167%
p99 memory usage: 59.865105%
Mean CPU usage: 79.71393%
p95 CPU usage: 97.73819%
p99 CPU usage: 98.55393%


-------------------------------------------------------

Test number 4
Test name: questions2 loaded => pair-lookup questions1

Statistics:

Insertion time: 0 ms
Querying time: 15723 ms
Total time: 15723 ms
Hit ratio: 1186/1666 (71.18848%)
Expected query hit ratio: 1091/1186 (91.98988%)
Query throughput: 105.95943 queries/second
Mean query latency: 150.2449 ms
p95 query latency: 179.0 ms
p99 percentile query latency: 193.0 ms
Mean memory usage: 59.49471%
p95 memory usage: 59.636826%
p99 memory usage: 59.6485%
Mean CPU usage: 73.18393%
p95 CPU usage: 81.487465%
p99 CPU usage: 82.44865%
```


