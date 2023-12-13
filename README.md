# Neo4j Vector Index CLI

This tool combines the [Neo4j Vector search](https://neo4j.com/docs/cypher-manual/current/indexes-for-vector-search/) capabilities with embeddings from [OpenAI](https://platform.openai.com/docs/api-reference/embeddings).

## Getting started
The code itself is working for the example below, but it is in a WIP state.
You can create your own binaries by invoking `./mvnw package`.

## Common usage
The most common workflow would be to create embeddings first and then query those embeddings with search phrases.
You have to have an [OpenAI API key](https://platform.openai.com/api-keys) to use this tool.
The tool expects the `OPENAI_API_KEY` environment variable to be set with a working key.

> [!NOTE]
> Please note that the `--label` parameter has to be set on the main command before invoking the subcommand.

### Create Embedding

_Example usage_
```shell
> neo4j-vector-cli --label Game create-embedding --properties name,about,description
```
This command will create embeddings for all nodes labeled `:Game` and join its fields _name_, _about_, and _description_
to be tokenized.
After getting the results back, it will store those on the nodes' property `embedding` per default.
This property is customizable, please have a look at the [Configuration](#configuration) section.

### Search for Embedding

_Example usage_
```shell
> neo4j-vector-cli --label Game search 'fun family strategy'
{records:[{__elementId__:"4:2..",__similarity__:0.8931642},{__elementId__:"4:1..",__similarity__:0.892818},...]}
```

The default output format might surprise at the first glance, but this output can be taken directly as input for [Neo4j's Cypher Shell](https://neo4j.com/docs/operations-manual/current/tools/cypher-shell/).

_Output as Cypher Shell input_
```shell
> cypher-shell 'UNWIND $records as record MATCH (g:Game)-[:DEVELOPED_BY]-(d) WHERE elementId(g) = record.__elementId__ RETURN g.name as game_name, g.metacritic_score as metacritic_score, d.name as developer, record.__similarity__ as similarity'"
Type " -P $(neo4j-vector-cli --label Game search 'fun family strategy')
```

This gives you the option to combine the vector search results with arbitrary Cypher statements for further processing.

If you are just interested in the results from the vector search themselves, use the `--format console` output.

```shell
> neo4j-vector-cli --label Game search 'family calm peaceful fun' --format console -p name
Labels                        Properties                                                                                                              Similarity
[Game]                        [name=Sheltered]                                                                                                        0.88821214
[Game]                        [name=Alba: A Wildlife Adventure]                                                                                       0.88753295
[Game]                        [name=Before We Leave]                                                                                                  0.88637364
[Game]                        [name=Shelter]                                                                                                          0.88603795
[Game]                        [name=Deep Sea Tycoon: Diver\'s Paradise]                                                                               0.88546705
```

![neo4j-vector-cli in action](docs/vector_cli.gif)

## Configuration
You can create a property file named `~/.neo4j-vector-cli.properties` containing the configuration:

```properties
neo4j-vector-cli.uri = neo4j://localhost:7687
neo4j-vector-cli.password = maybe_secure
```

All properties can be found by invoking the command `neo4j-vector-cli`:

```shell
Usage: neo4j-vector-cli [-v] [--embedding-property=<embeddingProperty>]
                        --label=<label> [--model=<model>]
                        [--password=<password>] [--uri=<uri>] [--user=<user>]
                        [COMMAND]
      --embedding-property=<embeddingProperty>
                        Property to be used for storing and searching the
                          embedding.
                        Defaults to 'embedding'.
      --label=<label>   Node label to be used for the operation.
      --model=<model>   Training model to be used.Defaults to
                          'text-embedding-ada-002'.
      --password=<password>
                        Neo4j password to be used.
      --uri=<uri>       Neo4j server URI to connect to.
                        Defaults to 'neo4j+s://967314e6.databases.neo4j.io'.
      --user=<user>     Neo4j user to be used.
                        Defaults to 'neo4j'.
  -v, --verbose         Verbose logging, defaults to 'false'.
Commands:
  search            Search with any phrase in the embeddings.
  create-embedding  Create embedding
```

or its subcommands.

_search_
```shell
Usage: neo4j-vector-cli search [-f=<format>] [-l=<limit>] [-t=<threshold>]
                               [-p=property[,property...]]... <phrase>
Search with any phrase in the embeddings.
      <phrase>            The search phrase.
  -f, --format=<format>   Output format, can be either 'parameter' or 'console'.
                          Parameter output can be used e.g. as input for
                            cypher-shell's -p(arameter) option.
                          Defaults to 'parameter'.
  -l, --limit=<limit>     Result size for search and output.
  -p, --properties=property[,property...]
                          Node properties to include in the result.
  -t, --threshold=<threshold>
                          Cap of accepted lower bound similarity (0.0 - 1.0).
```

_create-embedding_
```shell
Usage: neo4j-vector-cli create-embedding -p=property[,property...] [-p=property
       [,property...]]...
Create embedding
  -p, --properties=property[,property...]
         Node properties to include during embedding creation.
```
