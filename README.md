# git-swears
![GitHub Workflow Status](https://img.shields.io/github/workflow/status/vinceh121/git-swears/Java%20CI%20with%20Maven)
![Most used swear](https://img.shields.io/badge/dynamic/json?color=yellow&label=Most%20used%20swear&query=%24.mostUsed.word&url=https%3A%2F%2Fswear.vinceh121.me%2Fcount.json%3Furi%3Dhttps%3A%2F%2Fgithub.com%2Fvinceh121%2Fgit-swears)
![Swear count timeline of this project](https://swear.vinceh121.me/count.png?type=timeline&uri=https://github.com/vinceh121/git-swears)

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

### Using with shields.io

Using shields.io's dynamic json endpoint you can interface with the service to get beautiful badges, for example:

`https://img.shields.io/badge/dynamic/json?color=yellow&label=Most%20used%20swear&query=%24.mostUsed.word&url=https%3A%2F%2Fswear.vinceh121.me%2Fcount.json%3Furi%3Dhttps%3A%2F%2Fgithub.com%2Fvinceh121%2Fgit-swears`

## How to use the command line

**TBD**

## Why is the code running this ugly?
You think this is supposed to be a serious project?
