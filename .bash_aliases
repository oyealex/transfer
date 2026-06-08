# oper
alias rmf='rm -rf'
alias l='ls -CF'
alias lh='l -h'
alias ll='l -lF --time-style="+%Y-%m-%d %H:%M:%S"'
alias la='l -AF'
alias lla='ll -A'
alias llh='ll -h'

# git
alias gs='git status'
alias ga='git add'
alias gc='git commit'
alias gck='git checkout'
alias gd='git pull'
alias gdo='git pull origin'
alias gst='git stash'
alias grh='git reset -- HEAD'
alias gdf='git diff'
alias gdfc='git diff --cached'
alias gdfcs='git diff --cached --stat'
alias gdfs='git diff --stat'
alias gu='git push'

# dir
alias cdp='cd /mnt/d/project/'

# proxy
alias proxy='export https_proxy=http://$(cat /etc/resolv.conf |grep "nameserver" |cut -f 2 -d " "):33210 http_proxy=http://$(cat /etc/resolv.conf |grep "nameserver" |cut -f 2 -d " "):33210 all_proxy=socks5://$(cat /etc/resolv.conf |grep "nameserver" |cut -f 2 -d " "):33211'
alias noproxy='export https_proxy= http_proxy= all_proxy='
alias proxylan='curl -X PATCH http://$(cat /etc/resolv.conf |grep "nameserver" |cut -f 2 -d " "):33212/configs -H "Content-Type: application/json" -d "{\"allow-lan\":true}"'
