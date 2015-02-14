package com.core.dao.impl;

import java.io.Serializable;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.core.dao.OptionDAO;
import com.core.dao.generic.HibernateGenericRepository;
import com.core.domain.Option;

@Repository("optionDAO")
public class OptionDAOImpl extends HibernateGenericRepository<Option, Serializable> implements OptionDAO{
	
	
	
	

}
