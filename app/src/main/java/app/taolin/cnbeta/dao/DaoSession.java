package app.taolin.cnbeta.dao;

import java.util.Map;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.AbstractDaoSession;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.identityscope.IdentityScopeType;
import org.greenrobot.greendao.internal.DaoConfig;

import app.taolin.cnbeta.dao.FavorItem;
import app.taolin.cnbeta.dao.ListItem;
import app.taolin.cnbeta.dao.Article;
import app.taolin.cnbeta.dao.Headline;

import app.taolin.cnbeta.dao.FavorItemDao;
import app.taolin.cnbeta.dao.ListItemDao;
import app.taolin.cnbeta.dao.ArticleDao;
import app.taolin.cnbeta.dao.HeadlineDao;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.

/**
 * {@inheritDoc}
 * 
 * @see org.greenrobot.greendao.AbstractDaoSession
 */
public class DaoSession extends AbstractDaoSession {

    private final DaoConfig favorItemDaoConfig;
    private final DaoConfig listItemDaoConfig;
    private final DaoConfig articleDaoConfig;
    private final DaoConfig headlineDaoConfig;

    private final FavorItemDao favorItemDao;
    private final ListItemDao listItemDao;
    private final ArticleDao articleDao;
    private final HeadlineDao headlineDao;

    public DaoSession(Database db, IdentityScopeType type, Map<Class<? extends AbstractDao<?, ?>>, DaoConfig>
            daoConfigMap) {
        super(db);

        favorItemDaoConfig = daoConfigMap.get(FavorItemDao.class).clone();
        favorItemDaoConfig.initIdentityScope(type);

        listItemDaoConfig = daoConfigMap.get(ListItemDao.class).clone();
        listItemDaoConfig.initIdentityScope(type);

        articleDaoConfig = daoConfigMap.get(ArticleDao.class).clone();
        articleDaoConfig.initIdentityScope(type);

        headlineDaoConfig = daoConfigMap.get(HeadlineDao.class).clone();
        headlineDaoConfig.initIdentityScope(type);

        favorItemDao = new FavorItemDao(favorItemDaoConfig, this);
        listItemDao = new ListItemDao(listItemDaoConfig, this);
        articleDao = new ArticleDao(articleDaoConfig, this);
        headlineDao = new HeadlineDao(headlineDaoConfig, this);

        registerDao(FavorItem.class, favorItemDao);
        registerDao(ListItem.class, listItemDao);
        registerDao(Article.class, articleDao);
        registerDao(Headline.class, headlineDao);
    }
    
    public void clear() {
        favorItemDaoConfig.getIdentityScope().clear();
        listItemDaoConfig.getIdentityScope().clear();
        articleDaoConfig.getIdentityScope().clear();
        headlineDaoConfig.getIdentityScope().clear();
    }

    public FavorItemDao getFavorItemDao() {
        return favorItemDao;
    }

    public ListItemDao getListItemDao() {
        return listItemDao;
    }

    public ArticleDao getArticleDao() {
        return articleDao;
    }

    public HeadlineDao getHeadlineDao() {
        return headlineDao;
    }

}