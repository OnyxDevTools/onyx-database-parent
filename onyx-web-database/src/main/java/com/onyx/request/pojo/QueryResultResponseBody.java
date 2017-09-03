package com.onyx.request.pojo;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import java.util.List;

/**
 * Created by timothy.osborn on 4/13/15.
 *
 * Pojo for query result
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class)
public class QueryResultResponseBody
{
    private int maxResults;
    private List resultList;
    private int results;

    @SuppressWarnings("unused")
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

    public int getResults()
    {
        return results;
    }

    @SuppressWarnings("unused")
    public void setResults(int results)
    {
        this.results = results;
    }

    public List getResultList()
    {
        return resultList;
    }

    @SuppressWarnings("unused")
    public void setResultList(List resultList)
    {
        this.resultList = resultList;
    }

    public int getMaxResults()
    {
        return maxResults;
    }

    @SuppressWarnings("unused")
    public void setMaxResults(int maxResults)
    {
        this.maxResults = maxResults;
    }

}
