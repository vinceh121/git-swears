# git-swears
Counts swear words over time in a git repository. Available as a standalone executable and as a service.

## How to use the service

The JSON endpoint returning the count of swears is the following:

```
https://swear.vinceh121.me/count.json?uri=repo_uri[&branch=branch_name]
```

The endpoint returning PNG images of graphs is the following:

```
https://swear.vinceh121.me/count.png?type=graph_type&uri=repo_uri[&branch=branch_name]
```
*graph_type* is either `histogram` or `timeline`

## How to use the command line

**TBD**

## Why is the code runnig this ugly?
You think this is supposed to be a serious project?
