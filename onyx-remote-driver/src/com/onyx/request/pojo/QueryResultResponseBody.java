package com.onyx.request.pojo;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import java.util.List;

/**
 * Created by timothy.osborn on 4/13/15.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class QueryResultResponseBody
{
    protected int maxResults;
    protected int results;
    protected List resultList;

    public int getResults()
    {
        return results;
    }

    public void setResults(int results)
    {
        this.results = results;
    }

    public List getResultList()
    {
        return resultList;
    }

    public void setResultList(List resultList)
    {
        this.resultList = resultList;
    }

    public int getMaxResults()
    {
        return maxResults;
    }

    public void setMaxResults(int maxResults)
    {
        this.maxResults = maxResults;
    }

    public QueryResultResponseBody()
    {

    }

    public QueryResultResponseBody(int maxResults, int results)
    {
        this.maxResults = maxResults;
        this.results = results;
    }


    public QueryResultResponseBody(int maxResults, List results)
    {
        this.maxResults = maxResults;
        this.resultList = results;
    }
}
