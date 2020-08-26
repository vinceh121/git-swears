# git-swears
![GitHub Workflow Status](https://img.shields.io/github/workflow/status/vinceh121/git-swears/Java%20CI%20with%20Maven)

Counts swear words over time in a git repository. Available as a standalone executable and as a service.

## How to use the service

The JSON endpoint returning the count of swears is the following:

```
https://swear.vinceh121.me/count.json?uri=repo_uri[&branch=branch_name]
```

The endpoint returning PNG images of graphs is the following:

```
https://swear.vinceh121.me/count.png?type=timeline&uri=repo_uri[&branch=branch_name]
```

To know more on the endpoints, see the [wiki](https://github.com/vinceh121/git-swears/wiki/Endpoints)

## How to use the command line

**TBD**

## Why is the code runnig this ugly?
You think this is supposed to be a serious project?
