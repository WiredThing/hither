package system

import models.Repository

object FileBasedIndex extends Index {
  override  def allocateRepo(repo: Repository): Unit = {
    Configuration.buildRepoIndexPath(repo)
  }
}

trait Index {
  def allocateRepo(repo: Repository): Unit
}
